package com.studio.rec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 曲目时长硬额度 + 并发重核语义。
 * 种子（棚时均为 60 分钟）：
 * 预约1：晚风4 + 夜空3 = 7；预约2：城市3 = 3；
 * 预约4：晨雾5 + 微光4 + 归途3 = 12；预约6：旧巷5 + 远行4 = 9。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestWebConfig.class)
@Sql(scripts = "classpath:schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class TrackQuotaTests {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private TestRestTemplate http;
    @LocalServerPort
    private int port;
    private final ObjectMapper om = new ObjectMapper();

    private JsonNode readJson(MvcResult res) throws Exception {
        return om.readTree(res.getResponse().getContentAsByteArray());
    }

    /**
     * 并发用例必须走真实 HTTP：MockMvc 基于共享 ThreadLocal 请求上下文，从额外线程并发调用会
     * 串请求。真实端口 + TestRestTemplate 才等价于两名助理绕过页面直连接口。
     */
    private int postTrack(String body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> res = http.postForEntity(
                "http://127.0.0.1:" + port + "/api/tracks", new HttpEntity<>(body, h), String.class);
        return res.getStatusCode().value();
    }

    private int putTrack(long id, String body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> res = http.exchange(
                "http://127.0.0.1:" + port + "/api/tracks/" + id,
                org.springframework.http.HttpMethod.PUT, new HttpEntity<>(body, h), String.class);
        return res.getStatusCode().value();
    }

    private JsonNode usageOf(long bookingId) throws Exception {
        JsonNode arr = readJson(mvc.perform(get("/api/tracks/usage")).andReturn());
        for (JsonNode u : arr) {
            if (u.get("bookingId").asLong() == bookingId) {
                return u;
            }
        }
        throw new IllegalStateException("usage 中没有预约 " + bookingId);
    }

    @Test
    void 新增曲目超出预约棚时整笔退回_不留半条() throws Exception {
        // 预约1 已排 7，直接加 60 → 67 > 60，拒绝
        MvcResult r = mvc.perform(post("/api/tracks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":1,\"name\":\"超长大曲\",\"duration\":60}"))
                .andExpect(status().isBadRequest()).andReturn();
        assertTrue(readJson(r).get("message").asText().contains("超出"));
        // 整笔退回：半条曲目都不留，预约1 仍是原来两首
        mvc.perform(get("/api/tracks")).andExpect(jsonPath("$.length()").value(7));

        // 贴边 53：7 + 53 = 60，刚好等于棚时，可以保存
        mvc.perform(post("/api/tracks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":1,\"name\":\"压轴\",\"duration\":53}"))
                .andExpect(status().isOk());
        JsonNode u = usageOf(1);
        assertEquals(60, u.get("scheduledMinutes").asLong());
        assertEquals(0, u.get("remainingMinutes").asLong());

        // 额度用满后再新增 1 分钟也要拒绝（接口直连，绕过页面同样拦得住）
        mvc.perform(post("/api/tracks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":1,\"name\":\"再来一首\",\"duration\":1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/tracks")).andExpect(jsonPath("$.length()").value(8));
    }

    @Test
    void 修改曲目超限时原内容不变且不重复计算旧时长() throws Exception {
        // 预约1 已排 7：晚风 4 改成 54，7 - 4 + 54 = 57 ≤ 60（旧时长不重复计入，否则 7+54=61 会被误拒）
        mvc.perform(put("/api/tracks/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"duration\":54}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duration").value(54));
        mvc.perform(get("/api/tracks/2")).andExpect(jsonPath("$.duration").value(3));
        JsonNode u = usageOf(1);
        assertEquals(57, u.get("scheduledMinutes").asLong());
        assertEquals(3, u.get("remainingMinutes").asLong());

        // 再把晚风 54 改成 60：57 - 54 + 60 = 63 > 60，拒绝，曲目停在 54（原内容不变）
        MvcResult r = mvc.perform(put("/api/tracks/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"晚风加长版\",\"duration\":60}"))
                .andExpect(status().isBadRequest()).andReturn();
        assertTrue(readJson(r).get("message").asText().contains("超出"));
        mvc.perform(get("/api/tracks/1"))
                .andExpect(jsonPath("$.duration").value(54))
                .andExpect(jsonPath("$.name").value("晚风"));
        assertEquals(57, usageOf(1).get("scheduledMinutes").asLong());

        // 只改曲名不占额度：额度用紧也能保存
        mvc.perform(put("/api/tracks/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"晚风改字\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("晚风改字"));
    }

    @Test
    void 修改时长为零或负数被拒绝() throws Exception {
        mvc.perform(put("/api/tracks/1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"duration\":0}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/tracks/1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"duration\":-3}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/tracks/1")).andExpect(jsonPath("$.duration").value(4));
    }

    @Test
    void 额度账面接口按预约返回预约已排剩余分钟() throws Exception {
        JsonNode arr = readJson(mvc.perform(get("/api/tracks/usage")).andReturn());
        assertEquals(4, arr.size());
        JsonNode b1 = usageOf(1);
        assertEquals(60, b1.get("bookingMinutes").asLong());
        assertEquals(7, b1.get("scheduledMinutes").asLong());
        assertEquals(53, b1.get("remainingMinutes").asLong());
        JsonNode b4 = usageOf(4);
        assertEquals(12, b4.get("scheduledMinutes").asLong());
        assertEquals(48, b4.get("remainingMinutes").asLong());
    }

    @Test
    void 两名助理并发给同一预约新增_只成功一笔且账面收敛() throws Exception {
        // 预约2 已排 3，两人各自新增一首 57 分钟（单看都恰好 60 贴边）
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger bad = new AtomicInteger();
        AtomicReference<Throwable> err = new AtomicReference<>();
        Thread t1 = new Thread(() -> {
            try {
                ready.countDown();
                start.await();
                int code = postTrack("{\"bookingId\":2,\"name\":\"并发A\",\"duration\":57}");
                if (code == 200) ok.incrementAndGet(); else if (code == 400) bad.incrementAndGet();
            } catch (Throwable e) {
                err.set(e);
            }
        });
        Thread t2 = new Thread(() -> {
            try {
                ready.countDown();
                start.await();
                int code = postTrack("{\"bookingId\":2,\"name\":\"并发B\",\"duration\":57}");
                if (code == 200) ok.incrementAndGet(); else if (code == 400) bad.incrementAndGet();
            } catch (Throwable e) {
                err.set(e);
            }
        });
        t1.start();
        t2.start();
        ready.await();
        start.countDown();
        t1.join();
        t2.join();

        assertTrue(err.get() == null, "并发请求不应抛系统异常：" + err.get());
        assertEquals(1, ok.get(), "先保存的一笔成功");
        assertEquals(1, bad.get(), "后保存的一笔必须按最新总数被拒");
        // 终态账面：已排 57、剩余 3，失败一笔没有留下半条曲目
        JsonNode u = usageOf(2);
        assertEquals(57, u.get("scheduledMinutes").asLong());
        assertEquals(3, u.get("remainingMinutes").asLong());
        mvc.perform(get("/api/tracks")).andExpect(jsonPath("$.length()").value(8));
    }

    @Test
    void 并发加长不同曲目_只成功一笔且失败一笔原值不变() throws Exception {
        // 预约6 已排 9：旧巷 5→46（单看合计 50）、远行 4→45（单看也是 50），两笔同时提交
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger bad = new AtomicInteger();
        AtomicReference<Throwable> err = new AtomicReference<>();
        AtomicReference<String> body1 = new AtomicReference<>();
        AtomicReference<String> body2 = new AtomicReference<>();
        Thread t1 = new Thread(() -> {
            try {
                ready.countDown();
                start.await();
                int code = putTrack(9, "{\"duration\":46}");
                if (code == 200) ok.incrementAndGet(); else if (code == 400) bad.incrementAndGet();
            } catch (Throwable e) {
                err.set(e);
            }
        });
        Thread t2 = new Thread(() -> {
            try {
                ready.countDown();
                start.await();
                int code = putTrack(10, "{\"duration\":45}");
                if (code == 200) ok.incrementAndGet(); else if (code == 400) bad.incrementAndGet();
            } catch (Throwable e) {
                err.set(e);
            }
        });
        t1.start();
        t2.start();
        ready.await();
        start.countDown();
        t1.join();
        t2.join();

        assertTrue(err.get() == null, "并发请求不应抛系统异常：" + err.get());
        assertEquals(1, ok.get());
        assertEquals(1, bad.get());
        // 无论谁先拿到锁，终态已排都收敛为 50，绝不写爆 60
        assertEquals(50, usageOf(6).get("scheduledMinutes").asLong());
        int d9 = readJson(mvc.perform(get("/api/tracks/9")).andReturn()).get("duration").asInt();
        int d10 = readJson(mvc.perform(get("/api/tracks/10")).andReturn()).get("duration").asInt();
        // 成功者改成新值，失败者保持原时长（46+4 或 5+45 两种终态，合计都是 50）
        assertTrue((d9 == 46 && d10 == 4) || (d9 == 5 && d10 == 45),
                "失败一笔必须原值不变，实际 旧巷=" + d9 + " 远行=" + d10);
    }

    @Test
    void 失败后重试按当下账面重核_不沿用失败前快照() throws Exception {
        // 预约1 已排 7（晚风4 + 夜空3），先加一首 53 把额度用满（7 + 53 = 60）
        MvcResult created = mvc.perform(post("/api/tracks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":1,\"name\":\"占满曲\",\"duration\":53}"))
                .andExpect(status().isOk()).andReturn();
        long newId = readJson(created).get("id").asLong();

        // 额度已满：夜空 3→10 被拒（60 - 3 + 10 = 67），保持 3
        mvc.perform(put("/api/tracks/2").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"duration\":10}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/tracks/2")).andExpect(jsonPath("$.duration").value(3));

        // 占满曲被删、额度释放后重试：按当下账面 7 - 3 + 10 = 14 放行，不沿用"已满"的旧判断
        mvc.perform(delete("/api/tracks/" + newId)).andExpect(status().is2xxSuccessful());
        mvc.perform(put("/api/tracks/2").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"duration\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duration").value(10));
        JsonNode u = usageOf(1);
        assertEquals(14, u.get("scheduledMinutes").asLong());
        assertEquals(46, u.get("remainingMinutes").asLong());
    }
}
