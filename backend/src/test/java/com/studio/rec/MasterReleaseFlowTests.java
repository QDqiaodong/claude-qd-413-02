package com.studio.rec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestWebConfig.class)
@Sql(scripts = "classpath:schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class MasterReleaseFlowTests {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    private final ObjectMapper om = new ObjectMapper();

    private JsonNode createRelease(Long bookingId, long... trackIds) throws Exception {
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < trackIds.length; i++) {
            if (i > 0) ids.append(',');
            ids.append(trackIds[i]);
        }
        String body = "{\"bookingId\":" + bookingId + ",\"trackIds\":[" + ids + "]}";
        MvcResult res = mvc.perform(post("/api/master-releases")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return readJson(res);
    }

    private JsonNode createRejected(Long bookingId, long... trackIds) throws Exception {
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < trackIds.length; i++) {
            if (i > 0) ids.append(',');
            ids.append(trackIds[i]);
        }
        String body = "{\"bookingId\":" + bookingId + ",\"trackIds\":[" + ids + "]}";
        MvcResult res = mvc.perform(post("/api/master-releases")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andReturn();
        return readJson(res);
    }

    private JsonNode action(Long id, String path) throws Exception {
        MvcResult res = mvc.perform(post("/api/master-releases/" + id + "/" + path))
                .andExpect(status().isOk()).andReturn();
        return readJson(res);
    }

    private JsonNode readJson(MvcResult res) throws Exception {
        return om.readTree(res.getResponse().getContentAsByteArray());
    }

    @Test
    void 进行中和待确认的预约不许开单() throws Exception {
        JsonNode m1 = createRejected(1L, 1, 2);
        assertTrue(m1.get("message").asText().contains("只有已完成的预约"));
        JsonNode m2 = createRejected(2L, 8);
        assertTrue(m2.get("message").asText().contains("只有已完成的预约"));
    }

    @Test
    void 齐件不足两首或含未打星曲目不许开单() throws Exception {
        JsonNode onlyOne = createRejected(4L, 3);
        assertTrue(onlyOne.get("message").asText().contains("至少勾入两首"));

        JsonNode withUnrated = createRejected(4L, 3, 8);
        assertTrue(withUnrated.get("message").asText().contains("未打星"));

        JsonNode none = createRejected(4L);
        assertTrue(none.get("message").asText().contains("至少两首"));
    }

    @Test
    void 勾入其他预约的曲目不许开单() throws Exception {
        JsonNode cross = createRejected(4L, 3, 9);
        assertTrue(cross.get("message").asText().contains("不属于该预约"));
    }

    @Test
    void 正常开单提交会签到已放行() throws Exception {
        JsonNode r = createRelease(4L, 3, 7);
        long id = r.get("id").asLong();
        assertEquals("待齐件", r.get("status").asText());
        assertEquals(2, r.get("items").size());
        assertNotNull(r.get("releaseNo").asText());
        assertTrue(r.get("failReason").isNull());

        r = action(id, "submit-sign");
        assertEquals("已放行", r.get("status").asText());
        assertTrue(r.get("failReason").isNull());

        mvc.perform(get("/api/master-releases/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("已放行"))
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void 状态机不能跳步也不能倒回() throws Exception {
        JsonNode r = createRelease(6L, 9, 10);
        long id = r.get("id").asLong();

        // 待齐件不能直接重新会签
        mvc.perform(post("/api/master-releases/" + id + "/retry-sign"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("只有会签中的单据才能重新会签"));

        // 先解除维护，提交会签到已放行
        mvc.perform(put("/api/rooms/3").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"空闲\"}")).andExpect(status().isOk());
        action(id, "submit-sign");

        // 已放行不能再提交会签
        mvc.perform(post("/api/master-releases/" + id + "/submit-sign"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("只有待齐件的单据才能提交会签，不可跳步"));
        // 已放行不能改齐件 / 删除
        mvc.perform(put("/api/master-releases/" + id + "/items")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"trackIds\":[9,10]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/master-releases/" + id)).andExpect(status().isBadRequest());
    }

    @Test
    void 会签时房间维护按母带室口径拦截并停在会签中() throws Exception {
        JsonNode r = createRelease(6L, 9, 10); // 预约6 挂在 R003 鼓房（种子里就是维护）
        long id = r.get("id").asLong();

        r = action(id, "submit-sign");
        assertEquals("会签中", r.get("status").asText());
        String reason = r.get("failReason").asText();
        assertTrue(reason.contains("维护"), reason);
        assertTrue(reason.contains("柜子上锁"), reason);
        // 齐件一条不少、单据停在会签中，没有半成品状态
        assertEquals(2, r.get("items").size());
    }

    @Test
    void 房间恢复空闲后重新会签按实时数据放行() throws Exception {
        JsonNode r = createRelease(6L, 9, 10);
        long id = r.get("id").asLong();
        r = action(id, "submit-sign"); // 因维护失败
        assertEquals("会签中", r.get("status").asText());

        mvc.perform(put("/api/rooms/3").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"空闲\"}")).andExpect(status().isOk());

        r = action(id, "retry-sign");
        assertEquals("已放行", r.get("status").asText());
        assertTrue(r.get("failReason").isNull());
    }

    @Test
    void 会签中途掉星整单停会签中且无脏单_重签实时复核() throws Exception {
        JsonNode r = createRelease(4L, 3, 7);
        long id = r.get("id").asLong();

        // 提交会签前曲目7被清星
        mvc.perform(put("/api/tracks/7").contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":0}")).andExpect(status().isOk());

        r = action(id, "submit-sign");
        assertEquals("会签中", r.get("status").asText());
        assertTrue(r.get("failReason").asText().contains("没有星级"));
        assertTrue(r.get("failReason").asText().contains("微光"));
        // 齐件仍是勾入的两首，没有出现一半勾过一半没勾
        assertEquals(2, r.get("items").size());

        // 未恢复星级直接重新会签，仍按当前数据判失败（不能拿旧快照蒙混）
        r = action(id, "retry-sign");
        assertEquals("会签中", r.get("status").asText());
        assertTrue(r.get("failReason").asText().contains("没有星级"));

        // 重新打星后重新会签通过
        mvc.perform(put("/api/tracks/7").contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":5}")).andExpect(status().isOk());
        r = action(id, "retry-sign");
        assertEquals("已放行", r.get("status").asText());
        assertTrue(r.get("failReason").isNull());
    }

    @Test
    void 会签时预约不再是已完成则实时拦截() throws Exception {
        JsonNode r = createRelease(6L, 9, 10);
        long id = r.get("id").asLong();
        // 房间先解除维护，隔离出“预约掉出已完成”这一条失败原因
        mvc.perform(put("/api/rooms/3").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"空闲\"}")).andExpect(status().isOk());

        // 预约状态机本身禁止回退，这里从存储层模拟“会签中途预约不再是已完成”
        jdbc.update("UPDATE booking SET status = '进行中' WHERE id = 6");

        r = action(id, "submit-sign");
        assertEquals("会签中", r.get("status").asText());
        assertTrue(r.get("failReason").asText().contains("不再是已完成"));
        assertEquals(2, r.get("items").size());

        // 预约恢复已完成，重新会签按实时数据通过
        jdbc.update("UPDATE booking SET status = '已完成' WHERE id = 6");
        r = action(id, "retry-sign");
        assertEquals("已放行", r.get("status").asText());
        assertTrue(r.get("failReason").isNull());
    }

    @Test
    void 失败后重新勾齐件再会签全链路() throws Exception {
        JsonNode r = createRelease(6L, 9, 10);
        long id = r.get("id").asLong();
        action(id, "submit-sign"); // 维护失败

        mvc.perform(put("/api/rooms/3").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"空闲\"}")).andExpect(status().isOk());
        MvcResult res = mvc.perform(put("/api/master-releases/" + id + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trackIds\":[10,9]}"))
                .andExpect(status().isOk()).andReturn();
        JsonNode updated = readJson(res);
        assertTrue(updated.get("failReason").isNull());
        assertEquals(2, updated.get("items").size());

        r = action(id, "retry-sign");
        assertEquals("已放行", r.get("status").asText());
    }

    @Test
    void 待齐件单据可删除并连同齐件清理() throws Exception {
        JsonNode r = createRelease(4L, 3, 7);
        long id = r.get("id").asLong();
        mvc.perform(delete("/api/master-releases/" + id)).andExpect(status().isOk());
        mvc.perform(get("/api/master-releases/" + id)).andExpect(status().isBadRequest());
    }
}
