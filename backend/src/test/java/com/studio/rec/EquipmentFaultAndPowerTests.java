package com.studio.rec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 设备故障拆占用 + 全棚功率硬上限 + 并发串行语义。
 * 种子：上限 7000W；主录音棚 R1 占用 5000W（预约1 进行中）、排练室 R5 占用 1200W（预约5 进行中），
 * 人声室 R2 空闲 1500W（预约2 待确认）、鼓房 R3 维护 3000W、母带室 R4 空闲 2000W；当前已占合计 6200W。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestWebConfig.class)
@Sql(scripts = "classpath:schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class EquipmentFaultAndPowerTests {
    @Autowired
    private MockMvc mvc;
    private final ObjectMapper om = new ObjectMapper();

    private JsonNode readJson(MvcResult res) throws Exception {
        return om.readTree(res.getResponse().getContentAsByteArray());
    }

    @Test
    void 报故障当场拆掉进行中预约并把房间改回空闲() throws Exception {
        // 主电容麦克风（设备1，挂在 R1）报故障：预约1（进行中）当场打回待确认，R1 改回空闲
        mvc.perform(put("/api/equipments/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"故障\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("故障"));
        mvc.perform(get("/api/bookings/1")).andExpect(jsonPath("$.status").value("待确认"));
        mvc.perform(get("/api/rooms/1")).andExpect(jsonPath("$.status").value("空闲"));
    }

    @Test
    void 故障房间禁止新开和推进进行中_修好后再录() throws Exception {
        mvc.perform(put("/api/equipments/1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"故障\"}")).andExpect(status().isOk());

        // 旧的进行中已被打回待确认，故障没修好前不许再推进
        MvcResult r1 = mvc.perform(put("/api/bookings/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"进行中\"}"))
                .andExpect(status().isBadRequest()).andReturn();
        assertTrue(readJson(r1).get("message").asText().contains("故障"));
        // 也不许直接新开进行中场次
        MvcResult r2 = mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":1,\"custName\":\"临时场\",\"startMin\":700,\"endMin\":760,\"status\":\"进行中\"}"))
                .andExpect(status().isBadRequest()).andReturn();
        assertTrue(readJson(r2).get("message").asText().contains("故障"));
        // 待确认预约可以留着等修好（前台诉求）
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":1,\"custName\":\"排队场\",\"startMin\":700,\"endMin\":760,\"status\":\"待确认\"}"))
                .andExpect(status().isOk());
        // 设备修好后按当下状态可以重新推进
        mvc.perform(put("/api/equipments/1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"空闲\"}")).andExpect(status().isOk());
        mvc.perform(put("/api/bookings/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"进行中\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("进行中"));
    }

    @Test
    void 没有进行中预约的房间报故障不动房间状态() throws Exception {
        // 鼓房 R3 维护中、只有已完成预约：报故障只改设备状态，不把维护房改成空闲
        MvcResult res = mvc.perform(post("/api/equipments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"E902\",\"name\":\"鼓房话筒\",\"roomId\":3,\"type\":\"麦克风\",\"status\":\"空闲\"}"))
                .andExpect(status().isOk()).andReturn();
        long mic = readJson(res).get("id").asLong();

        mvc.perform(put("/api/equipments/" + mic).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"故障\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/rooms/3")).andExpect(jsonPath("$.status").value("维护"));
    }

    @Test
    void 新开预约先加总功率_超限整笔退回() throws Exception {
        // 已占 6200W，母带室 2000W 排进去就 8200W > 7000W：当场拦住，不是黄条提醒
        MvcResult r = mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":4,\"custName\":\"大场次\",\"startMin\":700,\"endMin\":760}"))
                .andExpect(status().isBadRequest()).andReturn();
        String msg = readJson(r).get("message").asText();
        assertTrue(msg.contains("功率") && msg.contains("超限"), msg);
        // 整笔退回：预约没落库，房间停在原状
        mvc.perform(get("/api/bookings")).andExpect(jsonPath("$.length()").value(4));
        mvc.perform(get("/api/rooms/4")).andExpect(jsonPath("$.status").value("空闲"));
        // 挂在已活跃房间的新预约不新增功率，可以落库
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":1,\"custName\":\"加场\",\"startMin\":700,\"endMin\":760}"))
                .andExpect(status().isOk());
    }

    @Test
    void 推进进行中超限当场拦住_解除后按当下状态重核() throws Exception {
        // 6200 + 人声室 1500 = 7700 > 7000，推进被拦，预约停在待确认
        MvcResult r = mvc.perform(put("/api/bookings/2").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"进行中\"}"))
                .andExpect(status().isBadRequest()).andReturn();
        assertTrue(readJson(r).get("message").asText().contains("超限"));
        mvc.perform(get("/api/bookings/2")).andExpect(jsonPath("$.status").value("待确认"));

        // 排练室退占用后合计降到 5000W，再点按当下数据重核放行（不用失败前的快照）
        mvc.perform(put("/api/rooms/5").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"空闲\"}")).andExpect(status().isOk());
        mvc.perform(put("/api/bookings/2").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"进行中\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("进行中"));
    }

    @Test
    void 先开后报故障与先报故障再开_两种顺序殊途同归() throws Exception {
        mvc.perform(put("/api/settings/power-limit").contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":10000}")).andExpect(status().isOk());
        MvcResult res = mvc.perform(post("/api/equipments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"E901\",\"name\":\"测试电容麦\",\"roomId\":2,\"type\":\"麦克风\",\"status\":\"空闲\"}"))
                .andExpect(status().isOk()).andReturn();
        long mic = readJson(res).get("id").asLong();

        // 顺序A：先把预约2推进进行中，再把电容麦报故障 → 占用被当场拆掉
        mvc.perform(put("/api/bookings/2").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"进行中\"}")).andExpect(status().isOk());
        mvc.perform(put("/api/equipments/" + mic).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"故障\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/bookings/2")).andExpect(jsonPath("$.status").value("待确认"));
        mvc.perform(get("/api/rooms/2")).andExpect(jsonPath("$.status").value("空闲"));
        mvc.perform(get("/api/equipments/" + mic)).andExpect(jsonPath("$.status").value("故障"));

        // 顺序B：故障状态下再开进行中 → 当场拦住，房间和预约停在原状
        mvc.perform(put("/api/bookings/2").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"进行中\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/bookings/2")).andExpect(jsonPath("$.status").value("待确认"));
        mvc.perform(get("/api/rooms/2")).andExpect(jsonPath("$.status").value("空闲"));
    }

    @Test
    void 报故障与开进行中并发_终态一致不按旧空闲落库() throws Exception {
        mvc.perform(put("/api/settings/power-limit").contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":10000}")).andExpect(status().isOk());
        MvcResult res = mvc.perform(post("/api/equipments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"E903\",\"name\":\"并发电容麦\",\"roomId\":2,\"type\":\"麦克风\",\"status\":\"空闲\"}"))
                .andExpect(status().isOk()).andReturn();
        long mic = readJson(res).get("id").asLong();

        // 一个人报故障、另一个人同时给同一房间开进行中，两笔并发起跑
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> faultErr = new AtomicReference<>();
        AtomicReference<Throwable> advanceErr = new AtomicReference<>();
        Thread t1 = new Thread(() -> {
            try {
                ready.countDown();
                start.await();
                mvc.perform(put("/api/equipments/" + mic).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"故障\"}"))
                        .andExpect(status().isOk());
            } catch (Throwable t) {
                faultErr.set(t);
            }
        });
        Thread t2 = new Thread(() -> {
            try {
                ready.countDown();
                start.await();
                // 可能成功（先跑），也可能被故障闸门拦住（后跑），两种都合法
                mvc.perform(put("/api/bookings/2").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"进行中\"}"));
            } catch (Throwable t) {
                advanceErr.set(t);
            }
        });
        t1.start();
        t2.start();
        ready.await();
        start.countDown();
        t1.join();
        t2.join();

        assertTrue(faultErr.get() == null, "报故障不应失败：" + faultErr.get());
        assertTrue(advanceErr.get() == null, "开进行中不应抛系统异常：" + advanceErr.get());
        // 无论谁先谁后，终态必须收敛：设备故障、预约待确认、房间空闲
        mvc.perform(get("/api/equipments/" + mic)).andExpect(jsonPath("$.status").value("故障"));
        mvc.perform(get("/api/bookings/2")).andExpect(jsonPath("$.status").value("待确认"));
        mvc.perform(get("/api/rooms/2")).andExpect(jsonPath("$.status").value("空闲"));
    }

    @Test
    void 完成预约不查功率_上限可调整且拒绝非法值() throws Exception {        mvc.perform(put("/api/settings/power-limit").contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":1}")).andExpect(status().isOk());
        mvc.perform(get("/api/settings/power-limit"))
                .andExpect(jsonPath("$.limit").value(1))
                .andExpect(jsonPath("$.inUse").value(6200));
        // 已完成是降载方向，不受上限拦截
        mvc.perform(put("/api/bookings/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"已完成\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("已完成"));
        // 非法上限当场拒绝
        mvc.perform(put("/api/settings/power-limit").contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":0}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/settings/power-limit").contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":-100}")).andExpect(status().isBadRequest());
    }
}
