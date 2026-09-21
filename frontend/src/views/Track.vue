<template>
  <div>
    <div class="bar"><h3>曲目排歌（棚时硬额度）</h3></div>
    <div class="rows">
      <div class="bcard" v-for="b in bookings" :key="b.id">
        <div class="head">
          <strong>{{ b.custName }}</strong>
          <span class="meta">{{ roomName(b.roomId) }} · {{ fmt(b.startMin) }} ~ {{ fmt(b.endMin) }}</span>
          <el-tag size="small" :type="statusType(b.status)">{{ b.status }}</el-tag>
          <span class="quota">
            预约分钟 <b>{{ usageOf(b.id).bookingMinutes }}</b> ｜
            已排分钟 <b :class="{ over: usageOf(b.id).remainingMinutes < 0 }">{{ usageOf(b.id).scheduledMinutes }}</b> ｜
            剩余分钟 <b :class="{ over: usageOf(b.id).remainingMinutes < 0 }">{{ usageOf(b.id).remainingMinutes }}</b>
          </span>
          <el-button size="small" type="primary" @click="openCreate(b)">+ 新增曲目</el-button>
        </div>
        <el-table :data="tracksOf(b.id)" border stripe size="small">
          <el-table-column prop="name" label="曲名" min-width="160" />
          <el-table-column label="时长(分)" width="100" prop="duration" />
          <el-table-column label="评分" width="230">
            <template #default="{ row }">
              <el-rate
                :model-value="row.rating || 0"
                @change="(v) => rate(row, v)"
                clearable
                show-score />
              <el-button v-if="row.rating" link type="danger" size="small" @click="rate(row, 0)">清星</el-button>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openEdit(b, row)">修改</el-button>
            </template>
          </el-table-column>
          <template #empty>该预约还没有曲目</template>
        </el-table>
      </div>
    </div>

    <el-dialog v-model="vis" :title="form.id ? '修改曲目' : '新增曲目'" width="420px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="归属预约">
          <span>{{ form.custName }} · 棚时 {{ usageOf(form.bookingId).bookingMinutes }} 分钟（硬额度）</span>
        </el-form-item>
        <el-form-item label="曲名">
          <el-input v-model="form.name" placeholder="曲名不能为空" />
        </el-form-item>
        <el-form-item label="时长(分)">
          <el-input-number v-model="form.duration" :min="1" :step="1" />
        </el-form-item>
        <el-form-item label="账面">
          <span>
            已排 {{ usageOf(form.bookingId).scheduledMinutes }} 分钟 ｜
            保存后合计 <b :class="{ over: projected > usageOf(form.bookingId).bookingMinutes }">{{ projected }}</b>
            / {{ usageOf(form.bookingId).bookingMinutes }} 分钟
          </span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="vis = false">取消</el-button>
        <!-- 不靠禁用按钮兜底：是否允许由后台按锁后最新总数硬判，页面仍可提交以暴露真实结果 -->
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import http from '../api'

const tracks = ref([])
const bookings = ref([])
const rooms = ref([])
const usage = ref([])
const vis = ref(false)
const saving = ref(false)
const form = ref({})

function tracksOf(bookingId) {
  return tracks.value.filter(t => t.bookingId === bookingId)
}
// 账面一律用后台汇总；usage 尚未加载到时先给 0，不影响保存后重新拉取对齐
function usageOf(bookingId) {
  return usage.value.find(u => u.bookingId === bookingId)
    || { bookingId, bookingMinutes: 0, scheduledMinutes: 0, remainingMinutes: 0 }
}
// 修改时保存后合计 = 已排 - 自己旧时长 + 新时长；新增时没有旧时长
const projected = computed(() => {
  const u = usageOf(form.value.bookingId)
  const oldDuration = form.value.oldDuration || 0
  return u.scheduledMinutes - oldDuration + (Number(form.value.duration) || 0)
})
function fmt(min) {
  const h = String(Math.floor(min / 60)).padStart(2, '0')
  const m = String(min % 60).padStart(2, '0')
  return h + ':' + m
}
function roomName(id) {
  const r = rooms.value.find(x => x.id === id)
  return r ? r.code + ' ' + (r.name || '') : '—'
}
function statusType(status) {
  return { '待确认': 'info', '进行中': 'warning', '已完成': 'success' }[status] || 'info'
}

function openCreate(b) {
  form.value = { bookingId: b.id, custName: b.custName, name: '', duration: 1, oldDuration: 0 }
  vis.value = true
}
function openEdit(b, row) {
  form.value = {
    id: row.id, bookingId: b.id, custName: b.custName,
    name: row.name, duration: row.duration, oldDuration: row.duration
  }
  vis.value = true
}
async function save() {
  saving.value = true
  try {
    const body = { name: form.value.name, duration: form.value.duration }
    if (form.value.id) {
      await http.put('/tracks/' + form.value.id, body)
    } else {
      await http.post('/tracks', { ...body, bookingId: form.value.bookingId })
    }
    vis.value = false
  } finally {
    // 成功、失败都重新拉后台账面：被硬额度拒绝时页面数字与库里完全一致，不沿用失败前快照
    saving.value = false
    await load()
  }
}
function rate(row, v) {
  return http.put('/tracks/' + row.id, { rating: v }).then(load, load)
}
async function load() {
  ;[tracks.value, bookings.value, rooms.value, usage.value] = await Promise.all([
    http.get('/tracks'), http.get('/bookings'), http.get('/rooms'), http.get('/tracks/usage')
  ])
}
onMounted(load)
</script>

<style scoped>
.bar { margin-bottom: 12px; }
.rows { display: flex; flex-direction: column; gap: 14px; }
.bcard { border: 1px solid #e9e4f5; border-radius: 12px; padding: 14px 18px; background: #fff; }
.head { display: flex; align-items: center; gap: 12px; margin-bottom: 8px; flex-wrap: wrap; }
.meta { color: #8a8499; font-size: 13px; }
.quota { color: #5c5570; font-size: 13px; }
.quota b { color: #30284a; }
.over { color: #e5484d !important; }
.head .el-button { margin-left: auto; }
</style>
