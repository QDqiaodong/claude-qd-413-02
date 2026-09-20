<template>
  <div>
    <div class="bar">
      <h3>预约流程</h3>
      <el-button type="primary" @click="openCreate">+ 新开预约</el-button>
    </div>
    <div class="rows">
      <div class="bcard" v-for="b in bookings" :key="b.id">
        <div class="head">
          <strong>{{ b.custName }}</strong>
          <span class="meta">{{ roomName(b.roomId) }} · {{ equipName(b.equipmentId) }}</span>
          <span class="meta">{{ fmt(b.startMin) }} ~ {{ fmt(b.endMin) }}</span>
        </div>
        <el-steps :active="stepActive(b.status)" align-center finish-status="success">
          <el-step title="待确认" />
          <el-step title="进行中" />
          <el-step title="已完成" />
        </el-steps>
        <div class="foot">
          <el-button
            type="primary" size="small"
            :disabled="b.status === '已完成'"
            @click="advance(b)">推进</el-button>
        </div>
      </div>
    </div>

    <el-dialog v-model="vis" title="新开预约" width="420px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="录音室">
          <el-select v-model="form.roomId" placeholder="选择录音室" style="width: 100%">
            <el-option v-for="r in rooms" :key="r.id" :label="r.code + ' ' + r.name" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="客户名称"><el-input v-model="form.custName" /></el-form-item>
        <el-form-item label="设备">
          <el-select v-model="form.equipmentId" clearable placeholder="可不选" style="width: 100%">
            <el-option v-for="e in roomEquipments" :key="e.id" :label="e.code + ' ' + e.name" :value="e.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="时段">
          <el-time-select v-model="form.start" start="08:00" end="22:00" step="00:30" placeholder="开始" style="width: 48%" />
          <el-time-select v-model="form.end" start="08:00" end="22:30" step="00:30" placeholder="结束" style="width: 48%; margin-left: 4%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="待确认" value="待确认" />
            <el-option label="进行中" value="进行中" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="vis = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import http from '../api'

const ORDER = ['待确认', '进行中', '已完成']
const bookings = ref([])
const rooms = ref([])
const equipments = ref([])
const vis = ref(false)
const form = ref({})

const roomEquipments = computed(() =>
  equipments.value.filter(e => e.roomId === form.value.roomId)
)

function stepActive(status) {
  const i = ORDER.indexOf(status)
  return status === '已完成' ? 3 : i
}
function fmt(min) {
  const h = String(Math.floor(min / 60)).padStart(2, '0')
  const m = String(min % 60).padStart(2, '0')
  return h + ':' + m
}
function toMin(hm) {
  if (!hm) return null
  const [h, m] = hm.split(':').map(Number)
  return h * 60 + m
}
function roomName(id) { const r = rooms.value.find(x => x.id === id); return r ? r.code + ' ' + (r.name || '') : '—' }
function equipName(id) {
  if (id == null) return '无设备'
  const e = equipments.value.find(x => x.id === id)
  return e ? e.code + ' ' + (e.name || '') : '—'
}
function advance(b) {
  const i = ORDER.indexOf(b.status)
  if (i < 0 || i >= ORDER.length - 1) return
  // 失败（故障房 / 功率超限 / 乐观锁）时错误消息由拦截器弹出，这里重新拉数据对齐当下状态
  return http.put('/bookings/' + b.id, { status: ORDER[i + 1] }).then(load, load)
}
function openCreate() {
  form.value = { roomId: null, custName: '', equipmentId: null, start: '10:00', end: '11:00', status: '待确认' }
  vis.value = true
}
async function save() {
  const body = {
    roomId: form.value.roomId,
    equipmentId: form.value.equipmentId || null,
    custName: form.value.custName,
    startMin: toMin(form.value.start),
    endMin: toMin(form.value.end),
    status: form.value.status
  }
  try {
    await http.post('/bookings', body)
    vis.value = false
  } finally {
    await load()
  }
}
async function load() {
  ;[bookings.value, rooms.value, equipments.value] = await Promise.all([
    http.get('/bookings'), http.get('/rooms'), http.get('/equipments')
  ])
}
onMounted(load)
</script>

<style scoped>
.bar { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.rows { display: flex; flex-direction: column; gap: 14px; }
.bcard { border: 1px solid #e9e4f5; border-radius: 12px; padding: 14px 18px; background: #fff; }
.head { display: flex; align-items: center; gap: 12px; margin-bottom: 6px; }
.meta { color: #8a8499; font-size: 13px; }
.foot { display: flex; justify-content: flex-end; margin-top: 4px; }
</style>
