<template>
  <div>
    <div class="bar">
      <h3>录音室占用率</h3>
      <el-button type="primary" @click="openCreate">+ 新增录音室</el-button>
    </div>

    <div class="power" v-if="power">
      <span class="txt">
        全棚功率：已占 {{ power.inUse }} W / 上限 {{ power.limit == null ? '未设置' : power.limit + ' W' }}
      </span>
      <el-progress
        class="pbar"
        :percentage="powerPct"
        :status="power.limit != null && power.inUse > power.limit ? 'exception' : (powerPct >= 80 ? 'warning' : '')"
        :stroke-width="10" />
      <el-button size="small" @click="openLimit">调整上限</el-button>
      <span class="tip">超限时新开预约 / 推进进行中会被当场拦住</span>
    </div>

    <div class="cards">
      <div class="card" v-for="r in rooms" :key="r.id">
        <div class="ring" :style="ringStyle(r)">
          <span>{{ occ(r) }}/{{ total(r) }}</span>
        </div>
        <div class="info">
          <div class="code">{{ r.code }} · {{ r.name }}</div>
          <el-select
            :model-value="r.status"
            size="small"
            class="status-select"
            @change="(v) => changeStatus(r, v)">
            <el-option label="空闲" value="空闲" />
            <el-option label="占用" value="占用" />
            <el-option label="维护" value="维护" />
          </el-select>
          <div class="note">功率 {{ r.power ?? '—' }} W</div>
        </div>
      </div>
    </div>

    <el-dialog v-model="vis" title="新增录音室">
      <el-form :model="form" label-width="80px">
        <el-form-item label="编号"><el-input v-model="form.code" placeholder="如 R006" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="功率(W)"><el-input-number v-model="form.power" :min="0" :step="100" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status">
            <el-option label="空闲" value="空闲" />
            <el-option label="占用" value="占用" />
            <el-option label="维护" value="维护" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="vis = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="limitVis" title="调整全棚功率上限" width="360px">
      <el-form label-width="80px">
        <el-form-item label="上限(W)">
          <el-input-number v-model="limitValue" :min="1" :step="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="limitVis = false">取消</el-button>
        <el-button type="primary" @click="saveLimit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import http from '../api'

const rooms = ref([])
const equipments = ref([])
const power = ref(null)
const vis = ref(false)
const form = ref({ status: '空闲', power: 1000 })
const limitVis = ref(false)
const limitValue = ref(7000)

const powerPct = computed(() => {
  if (!power.value || !power.value.limit) return 0
  return Math.min(100, Math.round(power.value.inUse / power.value.limit * 100))
})

function total(r) { return equipments.value.filter(e => e.roomId === r.id).length }
function occ(r) { return equipments.value.filter(e => e.roomId === r.id && e.status === '占用').length }
function ringStyle(r) {
  const pct = total(r) ? Math.round(occ(r) / total(r) * 100) : 0
  return { background: `conic-gradient(var(--el-color-primary) ${pct}%, #e9e4f5 ${pct}%)` }
}
async function load() {
  ;[rooms.value, equipments.value, power.value] = await Promise.all([
    http.get('/rooms'), http.get('/equipments'), http.get('/settings/power-limit')
  ])
}
function openCreate() { form.value = { status: '空闲', power: 1000 }; vis.value = true }
async function changeStatus(r, status) {
  await http.put('/rooms/' + r.id, { status })
  await load()
}
async function save() {
  await http.post('/rooms', form.value)
  vis.value = false
  await load()
}
function openLimit() {
  limitValue.value = power.value?.limit ?? 7000
  limitVis.value = true
}
async function saveLimit() {
  try {
    await http.put('/settings/power-limit', { value: limitValue.value })
    limitVis.value = false
  } finally {
    await load()
  }
}
onMounted(load)
</script>

<style scoped>
.bar { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.power {
  display: flex; align-items: center; gap: 14px; margin-bottom: 16px;
  border: 1px solid #e9e4f5; border-radius: 12px; padding: 10px 16px; background: #fff;
}
.power .txt { font-weight: 600; color: #4a3f6b; white-space: nowrap; }
.power .pbar { flex: 1; max-width: 320px; }
.power .tip { color: #8a8499; font-size: 12px; }
.cards { display: flex; flex-wrap: wrap; gap: 16px; }
.card {
  width: 230px; border: 1px solid #e9e4f5; border-radius: 14px; padding: 18px;
  display: flex; gap: 14px; align-items: center; background: #fff;
}
.ring {
  width: 78px; height: 78px; border-radius: 50%; display: flex; align-items: center; justify-content: center;
  font-weight: 700; color: #65469b; flex-shrink: 0;
}
.ring span { background: #fff; width: 54px; height: 54px; border-radius: 50%; display: flex; align-items: center; justify-content: center; }
.code { font-weight: 600; margin-bottom: 6px; }
.status-select { width: 104px; margin-bottom: 6px; }
.note { color: #8a8499; font-size: 13px; margin-top: 6px; }
</style>
