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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 预约棚时作为曲目分钟硬额度。
 * 种子：预约1（600~660=60 分，已排 晚风4 + 夜空3 = 7 分）；
 * 预约2（600~660=60 分，已排 城市3 分）；
 * 预约4（60 分，已排 5+4+3=12 分）；预约6（900~960=60 分，已排 5+4=9 分）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestWebConfig.class)
@Sql(scripts = "classpath:schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class TrackQuotaTests {
    @Autowired
    private MockMvc mvc;
    private final ObjectMapper om = new ObjectMapper();

    private JsonNode readJson(MvcResult res) throws Exception {
        return om.readTree(res.getResponse().getContentAsByteArray());
    }

    private MvcResult addTrack(long bookingId, String name, int duration) throws Exception {
        return mvc.perform(post("/api/tracks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":" + bookingId + ",\"name\":\"" + name + "\",\"duration\":" + duration + "}"))
                .andReturn();
    }

    private JsonNode budgetOf(long bookingId) throws Exception {
        MvcResult res = mvc.perform(get("/api/tracks/budgets")).andExpect(status().isOk()).andReturn();
        for (JsonNode b : readJson(res)) {
            if (b.get("bookingId").asLong() == bookingId) {
                return b;
            }
        }
        throw new IllegalStateException("预约 " + bookingId + " 没有账面");
    }

    @Test
    void 新增不超额可保存_账面同步增加() throws Exception {
        mvc.perform(post("/api/tracks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":1,\"name\":\"新曲\",\"duration\":50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duration").value(50));
        JsonNode b = budgetOf(1);
        assertEquals(60, b.get("bookingMin").asInt());
        assertEquals(57, b.get("scheduledMin").asInt());
        assertEquals(3, b.get("remainingMin").asInt());
    }

    @Test
    void 新增超过预约分钟明确拒绝_不留曲目_账面不变() throws Exception {
        // 已排 7，再加 54 -> 61 > 60
        MvcResult r = addTrack(1, "超长曲", 54);
        assertEquals(400, r.getResponse().getStatus());
        String msg = readJson(r).get("message").asText();
        assertTrue(msg.contains("超过预约棚时") && msg.contains("剩余 53"), msg);
        // 半条曲目都不能留下：还是两首
        mvc.perform(get("/api/tracks")).andExpect(jsonPath("$.length()").value(7));
        JsonNode b = budgetOf(1);
        assertEquals(7, b.get("scheduledMin").asInt());
        assertEquals(53, b.get("remainingMin").asInt());
    }

    @Test
    void 恰好等于预约分钟可以保存() throws Exception {
        // 已排 7，再加 53 -> 60 == 60，边界放行
        mvc.perform(post("/api/tracks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":1,\"name\":\"顶格曲\",\"duration\":53}"))
                .andExpect(status().isOk());
        assertEquals(0, budgetOf(1).get("remainingMin").asInt());
        // 再排 1 分钟都不行
        assertEquals(400, addTrack(1, "多一分钟", 1).getResponse().getStatus());
    }

    @Test
    void 修改加长超额被拒_原曲目内容保持不变() throws Exception {
        // 曲目2（夜空，3 分）改成 56：其他人共 4，4+56=60 恰好；改成 57 -> 61 超额
        mvc.perform(put("/api/tracks/2").contentType(MediaType.APPLICATION_JSON)
                .content("{\"duration\":56}")).andExpect(status().isOk());
        MvcResult r = mvc.perform(put("/api/tracks/2").contentType(MediaType.APPLICATION_JSON)
                .content("{\"duration\":57}")).andExpect(status().isBadRequest()).andReturn();
        assertTrue(readJson(r).get("message").asText().contains("超过预约棚时"));
        // 时长停在 56，名字也不变
        mvc.perform(get("/api/tracks/2"))
                .andExpect(jsonPath("$.duration").value(56))
                .andExpect(jsonPath("$.name").value("夜空"));
        assertEquals(60, budgetOf(1).get("scheduledMin").asInt());
    }

    @Test
    void 修改时不把自己旧时长重复计算() throws Exception {
        // 预约1 已排 7（晚风4+夜空3）。把晚风从 4 改成 56：剔除自己旧值后 3+56=59 <= 60，应成功；
        // 若错误地把旧 4 也算进去（4+3+56=63）就会误拒
        mvc.perform(put("/api/tracks/1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"duration\":56}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.duration").value(56));
        JsonNode b = budgetOf(1);
        assertEquals(59, b.get("scheduledMin").asInt());
        assertEquals(1, b.get("remainingMin").asInt());
    }

    @Test
    void 缩短曲目释放余量_改名与只改评分不动总额() throws Exception {
        // 晚风 4 -> 2，释放 2 分钟
        mvc.perform(put("/api/tracks/1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"duration\":2}")).andExpect(status().isOk());
        assertEquals(5, budgetOf(1).get("scheduledMin").asInt());
        // 只改名字，总分钟不变
        mvc.perform(put("/api/tracks/1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"晚风新版\"}")).andExpect(status().isOk());
        assertEquals(5, budgetOf(1).get("scheduledMin").asInt());
        // 只改评分走同一保存路径，不影响额度
        mvc.perform(put("/api/tracks/1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":5}")).andExpect(status().isOk());
        assertEquals(5, budgetOf(1).get("scheduledMin").asInt());
        // 非法时长被拒
        mvc.perform(put("/api/tracks/1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"duration\":0}")).andExpect(status().isBadRequest());
    }

    @Test
    void 绕过页面直连后台新增和修改一样被拦() throws Exception {
        // 测试种子里预约2暂无曲目：先直连后台排一首 3 分钟，再裸 JSON 硬闯
        MvcResult seed = addTrack(2, "城市", 3);
        assertEquals(200, seed.getResponse().getStatus());
        long cityId = readJson(seed).get("id").asLong();
        // 裸 JSON、不走任何前端按钮：新增超限（3+58=61）
        assertEquals(400, addTrack(2, "硬闯新增", 58).getResponse().getStatus());
        // 直接 PUT 把该曲目加长到 61 一样被拦，原值不动
        mvc.perform(put("/api/tracks/" + cityId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"duration\":61}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/tracks/" + cityId)).andExpect(jsonPath("$.duration").value(3));
    }

    @Test
    void 两名助理并发新增_先存扣余量_后存按最新合计被拒_总数不爆() throws Exception {
        // 测试种子里预约2暂无曲目：先排一首 3 分钟（对应生产种子的"城市"）
        mvc.perform(post("/api/tracks").contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookingId\":2,\"name\":\"城市\",\"duration\":3}")).andExpect(status().isOk());
        // 60 分钟额度，两笔各 55 分钟（任一笔单独都可存：3+55=58）
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        Runnable req = () -> {
            try {
                ready.countDown();
                start.await();
                int code = addTrack(2, "并发新歌", 55).getResponse().getStatus();
                if (code == 200) {
                    ok.incrementAndGet();
                } else if (code == 400) {
                    rejected.incrementAndGet();
                }
            } catch (Exception ignored) {
            }
        };
        Thread t1 = new Thread(req);
        Thread t2 = new Thread(req);
        t1.start();
        t2.start();
        ready.await();
        start.countDown();
        t1.join();
        t2.join();

        assertEquals(1, ok.get(), "恰好一笔成功");
        assertEquals(1, rejected.get(), "后到的一笔必须被拒");
        // 终态账面：3 + 55 = 58，绝不会是 113
        JsonNode b = budgetOf(2);
        assertEquals(58, b.get("scheduledMin").asInt());
        assertEquals(2, b.get("remainingMin").asInt());
    }

    @Test
    void 并发加长不同曲目_只有先到者生效() throws Exception {
        // 预约4：60 分钟，已排 晨雾5+微光4+归途3=12。
        // 甲把晨雾(3号)加到 40；乙把微光(7号)加到 40。两笔各自拿旧余量都算得过：
        // 甲口径 40+4+3=47、乙口径 5+40+3=48；串行后后到者必须按先到者提交后的最新合计重判：
        // 若甲先（合计47），乙为 47-4+40=83 > 60，被拒；反之同理，恰好一笔成功。
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        Thread t1 = new Thread(() -> {
            try {
                ready.countDown();
                start.await();
                int code = mvc.perform(put("/api/tracks/3").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"duration\":40}")).andReturn().getResponse().getStatus();
                if (code == 200) ok.incrementAndGet(); else if (code == 400) rejected.incrementAndGet();
            } catch (Exception ignored) {
            }
        });
        Thread t2 = new Thread(() -> {
            try {
                ready.countDown();
                start.await();
                int code = mvc.perform(put("/api/tracks/7").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"duration\":40}")).andReturn().getResponse().getStatus();
                if (code == 200) ok.incrementAndGet(); else if (code == 400) rejected.incrementAndGet();
            } catch (Exception ignored) {
            }
        });
        t1.start();
        t2.start();
        ready.await();
        start.countDown();
        t1.join();
        t2.join();

        assertEquals(1, ok.get());
        assertEquals(1, rejected.get());
        // 恰好一首被加长到 40，另一首停在原值；总分钟绝不写爆
        int d3 = readJson(mvc.perform(get("/api/tracks/3")).andReturn()).get("duration").asInt();
        int d7 = readJson(mvc.perform(get("/api/tracks/7")).andReturn()).get("duration").asInt();
        assertEquals(1, (d3 == 40 ? 1 : 0) + (d7 == 40 ? 1 : 0), "只有先到者的加长生效");
        JsonNode b = budgetOf(4);
        // 晨雾先 -> 40+4+3=47；微光先 -> 5+40+3=48
        int expected = d3 == 40 ? 47 : 48;
        assertEquals(expected, b.get("scheduledMin").asInt());
        assertTrue(b.get("scheduledMin").asInt() <= 60, "总分钟不得写爆，实际 " + b.get("scheduledMin").asInt());
    }

    @Test
    void 被拒后重试按最新账面判断_不沿用旧数字() throws Exception {
        // 先让 53 分的歌把预约1 顶到 60，再尝试加 10 分被拒
        mvc.perform(post("/api/tracks").contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookingId\":1,\"name\":\"顶格\",\"duration\":53}")).andExpect(status().isOk());
        assertEquals(400, addTrack(1, "再来一首", 10).getResponse().getStatus());
        // 删掉顶格歌后释放余量，同样的重试请求应按当下账面成功
        long topId = -1;
        for (JsonNode t : readJson(mvc.perform(get("/api/tracks")).andReturn())) {
            if ("顶格".equals(t.get("name").asText())) {
                topId = t.get("id").asLong();
            }
        }
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/tracks/" + topId))
                .andExpect(status().isOk());
        mvc.perform(post("/api/tracks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":1,\"name\":\"再来一首\",\"duration\":10}"))
                .andExpect(status().isOk());
        assertEquals(17, budgetOf(1).get("scheduledMin").asInt());
    }
}
