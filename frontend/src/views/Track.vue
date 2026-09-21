<template>
  <div>
    <div class="bar">
      <h3>曲目排歌</h3>
      <el-button type="primary" @click="openCreate">+ 新增曲目</el-button>
      <span class="rule">预约起止时段是本组曲目的硬额度：总分钟超过预约分钟，后台会整笔退回</span>
    </div>

    <div class="rows">
      <div class="bcard" v-for="b in bookings" :key="b.id">
        <div class="head">
          <strong>#{{ b.id }} {{ b.custName }}</strong>
          <span class="meta">{{ roomName(b.roomId) }} · {{ fmt(b.startMin) }} ~ {{ fmt(b.endMin) }}</span>
          <el-tag
            :type="budget(b.id).remaining < 0 ? 'danger' : budget(b.id).remaining === 0 ? 'warning' : 'success'"
            size="small">
            预约 {{ budget(b.id).bookingMin }} 分 · 已排 {{ budget(b.id).scheduledMin }} 分 ·
            剩余 {{ budget(b.id).remaining }} 分
          </el-tag>
        </div>

        <el-table :data="tracksOf(b.id)" border size="small">
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
          <el-table-column label="操作" width="140">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openEdit(row)">修改</el-button>
              <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
            </template>
          </el-table-column>
          <template #empty>暂无曲目</template>
        </el-table>

        <div class="foot">
          <el-button
            type="primary" size="small" plain
            :disabled="budget(b.id).remaining <= 0"
            @click="openCreateFor(b.id)">+ 给该预约加曲目</el-button>
          <span v-if="budget(b.id).remaining <= 0" class="meta">该预约棚时已排满，请先删歌或缩短时长</span>
        </div>
      </div>
    </div>

    <el-dialog v-model="dlg.visible" :title="dlg.isEdit ? '修改曲目' : '新增曲目'" width="420px">
      <el-form :model="dlg" label-width="92px">
        <el-form-item label="归属预约">
          <el-select v-model="dlg.bookingId" :disabled="dlg.isEdit" placeholder="选择预约" style="width: 100%">
            <el-option
              v-for="b in bookings"
              :key="b.id"
              :label="'#' + b.id + ' ' + b.custName + '（剩余 ' + budget(b.id).remaining + ' 分）'"
              :value="b.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="曲名"><el-input v-model="dlg.name" placeholder="曲名不能为空" /></el-form-item>
        <el-form-item label="时长(分)">
          <el-input-number v-model="dlg.duration" :min="1" :step="1" />
        </el-form-item>
        <el-alert
          v-if="dlg.bookingId"
          type="info" :closable="false" show-icon class="hint"
          :title="'该预约：预约 ' + budget(dlg.bookingId).bookingMin + ' 分、已排 '
            + budget(dlg.bookingId).scheduledMin + ' 分、剩余 '
            + (dlg.isEdit ? budget(dlg.bookingId).remaining + dlg.oldDuration : budget(dlg.bookingId).remaining) + ' 分'" />
      </el-form>
      <template #footer>
        <el-button @click="dlg.visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api'

const tracks = ref([])
const bookings = ref([])
const rooms = ref([])
const budgets = ref([])

const dlg = reactive({
  visible: false,
  isEdit: false,
  trackId: null,
  bookingId: null,
  name: '',
  duration: 1,
  oldDuration: 0
})

function budget(bookingId) {
  const b = budgets.value.find(x => x.bookingId === bookingId)
  return b ? b : { bookingMin: 0, scheduledMin: 0, remaining: 0 }
}
function tracksOf(bookingId) {
  return tracks.value.filter(t => t.bookingId === bookingId)
}
function roomName(id) {
  const r = rooms.value.find(x => x.id === id)
  return r ? r.code + ' ' + (r.name || '') : '—'
}
function fmt(min) {
  const h = String(Math.floor(min / 60)).padStart(2, '0')
  const m = String(min % 60).padStart(2, '0')
  return h + ':' + m
}

function openCreate() {
  Object.assign(dlg, { visible: true, isEdit: false, trackId: null, bookingId: null, name: '', duration: 1, oldDuration: 0 })
}
function openCreateFor(bookingId) {
  Object.assign(dlg, { visible: true, isEdit: false, trackId: null, bookingId, name: '', duration: 1, oldDuration: 0 })
}
function openEdit(row) {
  Object.assign(dlg, {
    visible: true, isEdit: true, trackId: row.id, bookingId: row.bookingId,
    name: row.name, duration: row.duration, oldDuration: row.duration
  })
}

async function save() {
  if (!dlg.bookingId) {
    ElMessage.error('请选择归属预约')
    return
  }
  if (!dlg.name || !dlg.name.trim()) {
    ElMessage.error('曲名不能为空')
    return
  }
  if (!dlg.duration || dlg.duration <= 0) {
    ElMessage.error('时长需大于 0')
    return
  }
  const body = { bookingId: dlg.bookingId, name: dlg.name.trim(), duration: dlg.duration }
  try {
    if (dlg.isEdit) {
      await http.put('/tracks/' + dlg.trackId, body)
    } else {
      await http.post('/tracks', body)
    }
    dlg.visible = false
  } catch (e) {
    // 被额度拒绝 / 并发撞锁：错误消息已由拦截器弹出。弹窗保留便于改数重试，
    // 账面必须重新拉取——重试不能沿用失败前的旧剩余数字
  } finally {
    await load()
    if (dlg.isEdit) {
      const fresh = tracks.value.find(t => t.id === dlg.trackId)
      if (fresh) {
        dlg.bookingId = fresh.bookingId
        dlg.name = fresh.name
        dlg.duration = fresh.duration
        dlg.oldDuration = fresh.duration
      }
    }
  }
}

async function rate(row, v) {
  try {
    await http.put('/tracks/' + row.id, { rating: v })
  } catch (e) {
    // 评分本身不改时长；失败仍按后台账面刷新
  } finally {
    await load()
  }
}

async function remove(row) {
  await ElMessageBox.confirm('确认删除曲目《' + row.name + '》？删除后释放 ' + row.duration + ' 分钟', '提示', { type: 'warning' })
  await http.delete('/tracks/' + row.id)
  ElMessage.success('已删除，剩余分钟已释放')
  await load()
}

async function load() {
  // 三项账面以后台 /tracks/budgets 为准，刷新后与后台一致
  const [ts, bs, rs, bg] = await Promise.all([
    http.get('/tracks'), http.get('/bookings'), http.get('/rooms'), http.get('/tracks/budgets')
  ])
  tracks.value = ts
  bookings.value = bs
  rooms.value = rs
  budgets.value = bg
}
onMounted(load)
</script>

<style scoped>
.bar { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.rule { color: #8a8499; font-size: 12px; }
.rows { display: flex; flex-direction: column; gap: 14px; }
.bcard { border: 1px solid #e9e4f5; border-radius: 12px; padding: 14px 18px; background: #fff; }
.head { display: flex; align-items: center; gap: 12px; margin-bottom: 8px; flex-wrap: wrap; }
.meta { color: #8a8499; font-size: 13px; }
.foot { display: flex; justify-content: flex-end; align-items: center; gap: 10px; margin-top: 8px; }
.hint { margin-top: 4px; }
</style>
