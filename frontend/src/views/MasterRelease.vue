<template>
  <div>
    <div class="bar">
      <h3>母带放行台</h3>
      <el-button type="primary" @click="openCreate">+ 开放行单</el-button>
    </div>

    <el-empty v-if="releases.length === 0" description="还没有放行单" />

    <div class="rows">
      <div class="rcard" v-for="r in releases" :key="r.id">
        <div class="head">
          <strong>{{ r.releaseNo }}</strong>
          <el-tag size="small" :type="statusTag(r)">{{ r.status }}</el-tag>
          <span class="meta">
            预约 #{{ r.bookingId }} {{ r.custName }} ·
            {{ r.roomCode }} {{ r.roomName }}
            <el-tag size="small" :type="r.roomStatus === '维护' ? 'warning' : 'info'">{{ r.roomStatus || '—' }}</el-tag>
            预约状态：{{ r.bookingStatus || '—' }}
          </span>
        </div>

        <el-steps :active="stepActive(r)" align-center class="steps">
          <el-step title="待齐件" />
          <el-step title="会签中" :status="r.failReason ? 'error' : undefined" />
          <el-step title="已放行" />
        </el-steps>

        <el-alert
          v-if="r.failReason"
          class="fail"
          type="error"
          :closable="false"
          show-icon
          title="会签未通过，整单停在会签中"
          :description="r.failReason"
        />

        <div class="items">
          <el-tag
            v-for="it in r.items"
            :key="it.itemId"
            size="small"
            :type="it.rating ? 'success' : 'danger'"
            class="itag">
            {{ it.name || ('曲目#' + it.trackId) }}
            <span v-if="it.rating"> ★{{ it.rating }}</span>
            <span v-else>（未打星）</span>
          </el-tag>
          <span v-if="r.items.length === 0" class="meta">无齐件</span>
        </div>

        <div class="foot">
          <template v-if="r.status === '待齐件'">
            <el-button size="small" @click="openEdit(r)">勾齐件</el-button>
            <el-button size="small" type="primary" @click="submitSign(r)">提交会签</el-button>
            <el-button size="small" type="danger" plain @click="remove(r)">删除</el-button>
          </template>
          <template v-else-if="r.status === '会签中' && r.failReason">
            <el-button size="small" @click="openEdit(r)">重新勾齐件</el-button>
            <el-button size="small" type="warning" @click="retrySign(r)">重新会签</el-button>
          </template>
          <template v-else-if="r.status === '会签中'">
            <span class="meta">会签流转中…</span>
          </template>
          <template v-else>
            <el-tag type="success" size="small">母带已出棚</el-tag>
          </template>
        </div>
      </div>
    </div>

    <!-- 开单 / 勾齐件 -->
    <el-dialog v-model="dlg.visible" :title="dlg.isEdit ? '重新勾齐件（按当前实时数据核）' : '开母带放行单'" width="640px">
      <el-form label-width="92px">
        <el-form-item label="挂载预约" v-if="!dlg.isEdit">
          <el-select v-model="dlg.bookingId" placeholder="只有已完成的预约可开单" @change="onBookingChange" style="width: 100%">
            <el-option
              v-for="b in completedBookings"
              :key="b.id"
              :label="'#' + b.id + ' ' + b.custName + '（' + roomText(b.roomId) + '）'"
              :value="b.id" />
          </el-select>
          <div class="hint">进行中 / 待确认的预约不允许开单</div>
        </el-form-item>
        <el-form-item v-else label="挂载预约">
          <span class="meta">#{{ dlg.bookingId }} {{ dlg.custName }} · {{ dlg.roomText }}（预约：{{ dlg.bookingStatus }}）</span>
        </el-form-item>

        <el-alert
          v-if="dlg.bookingId && roomOf(dlg.bookingId)?.status === '维护'"
          type="warning" :closable="false" show-icon class="hint-block"
          title="该预约的录音室当前处于维护中：按母带室口径，柜子上锁无法取带，会签不会放行" />

        <el-form-item label="齐件曲目">
          <el-table
            ref="tableRef"
            :data="dlg.tracks"
            border
            size="small"
            max-height="280"
            row-key="id"
            @selection-change="onSelectionChange">
            <el-table-column type="selection" width="48" :selectable="canSelect" reserve-selection />
            <el-table-column prop="name" label="曲名" min-width="160" />
            <el-table-column label="星级" width="150">
              <template #default="{ row }">
                <el-rate v-if="row.rating" :model-value="row.rating" disabled />
                <el-tag v-else type="danger" size="small">未打星，不可勾</el-tag>
              </template>
            </el-table-column>
          </el-table>
          <div class="hint">
            至少勾入 2 首已打星曲目；未打星的曲目勾不进来。已选 {{ dlg.selected.length }} 首
          </div>
          <el-alert
            v-for="name in dlg.droppedNames" :key="name"
            type="error" :closable="false" show-icon class="hint-block"
            :title="'原已勾入的《' + name + '》当前已掉星，重新会签前必须处理'" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg.visible = false">取消</el-button>
        <el-button type="primary" @click="saveDlg">
          {{ dlg.isEdit ? '保存齐件' : '开单（待齐件）' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api'

const releases = ref([])
const bookings = ref([])
const rooms = ref([])
const tracks = ref([])
const tableRef = ref(null)

const dlg = reactive({
  visible: false,
  isEdit: false,
  releaseId: null,
  bookingId: null,
  custName: '',
  roomText: '',
  bookingStatus: '',
  tracks: [],
  selected: [],
  droppedNames: []
})

const completedBookings = computed(() => bookings.value.filter(b => b.status === '已完成'))

function stepActive(r) {
  if (r.status === '已放行') return 3
  if (r.status === '会签中') return 1
  return 0
}
function statusTag(r) {
  if (r.status === '已放行') return 'success'
  if (r.status === '会签中') return r.failReason ? 'danger' : 'warning'
  return 'info'
}
function roomOf(bookingId) {
  const b = bookings.value.find(x => x.id === bookingId)
  return b ? rooms.value.find(r => r.id === b.roomId) : null
}
function roomText(roomId) {
  const r = rooms.value.find(x => x.id === roomId)
  return r ? r.code + ' ' + r.name : '—'
}
function canSelect(row) {
  return row.rating != null
}
function onSelectionChange(rows) {
  dlg.selected = rows
}
function onBookingChange(bookingId) {
  dlg.tracks = tracks.value.filter(t => t.bookingId === bookingId)
  dlg.selected = []
  dlg.droppedNames = []
  tableRef.value?.clearSelection()
}

async function openCreate() {
  Object.assign(dlg, {
    visible: true, isEdit: false, releaseId: null,
    bookingId: null, custName: '', roomText: '', bookingStatus: '',
    tracks: [], selected: [], droppedNames: []
  })
  await nextTickFrame()
  tableRef.value?.clearSelection()
  if (completedBookings.value.length === 0) {
    ElMessage.warning('当前没有已完成的预约，无法开单')
  }
}

async function openEdit(r) {
  Object.assign(dlg, {
    visible: true, isEdit: true, releaseId: r.id,
    bookingId: r.bookingId,
    custName: r.custName,
    roomText: (r.roomCode || '') + ' ' + (r.roomName || ''),
    bookingStatus: r.bookingStatus,
    tracks: tracks.value.filter(t => t.bookingId === r.bookingId),
    selected: [],
    droppedNames: r.items.filter(it => it.rating == null).map(it => it.name || ('曲目#' + it.trackId))
  })
  // 预勾当前仍有星级的齐件；掉星的不预勾，避免脏勾
  const ratedCheckedIds = new Set(r.items.filter(it => it.rating != null).map(it => it.trackId))
  await nextTickFrame()
  tableRef.value?.clearSelection()
  dlg.tracks.forEach(t => {
    if (ratedCheckedIds.has(t.id)) tableRef.value?.toggleRowSelection(t, true)
  })
}
function nextTickFrame() {
  return new Promise(resolve => setTimeout(resolve, 30))
}

async function saveDlg() {
  const trackIds = dlg.selected.map(t => t.id)
  if (trackIds.length < 2) {
    ElMessage.error('至少勾入两首已打星曲目')
    return
  }
  if (dlg.isEdit) {
    await http.put('/master-releases/' + dlg.releaseId + '/items', { trackIds })
    ElMessage.success('齐件已按当前数据更新')
  } else {
    if (!dlg.bookingId) {
      ElMessage.error('请选择已完成的预约')
      return
    }
    await http.post('/master-releases', { bookingId: dlg.bookingId, trackIds })
    ElMessage.success('已开单，进入待齐件')
  }
  dlg.visible = false
  await load()
}

async function submitSign(r) {
  const res = await http.post('/master-releases/' + r.id + '/submit-sign')
  if (res.failReason) ElMessage.warning('会签未通过，单据停在会签中')
  else ElMessage.success('会签通过，母带已放行')
  await load()
}
async function retrySign(r) {
  const res = await http.post('/master-releases/' + r.id + '/retry-sign')
  if (res.failReason) ElMessage.warning('重新会签仍未通过')
  else ElMessage.success('重新会签通过，母带已放行')
  await load()
}
async function remove(r) {
  await ElMessageBox.confirm('确认删除放行单 ' + r.releaseNo + ' ？', '提示', { type: 'warning' })
  await http.delete('/master-releases/' + r.id)
  await load()
}

async function load() {
  ;[releases.value, bookings.value, rooms.value, tracks.value] = await Promise.all([
    http.get('/master-releases'), http.get('/bookings'), http.get('/rooms'), http.get('/tracks')
  ])
}
onMounted(load)
</script>

<style scoped>
.bar { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.rows { display: flex; flex-direction: column; gap: 14px; }
.rcard { border: 1px solid #e9e4f5; border-radius: 12px; padding: 14px 18px; background: #fff; }
.head { display: flex; align-items: center; gap: 12px; margin-bottom: 10px; flex-wrap: wrap; }
.meta { color: #8a8499; font-size: 13px; }
.steps { margin: 6px 0 10px; }
.fail { margin: 6px 0 10px; }
.items { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 8px; }
.itag { font-size: 13px; }
.foot { display: flex; justify-content: flex-end; gap: 8px; align-items: center; }
.hint { color: #8a8499; font-size: 12px; margin-top: 6px; }
.hint-block { margin-top: 8px; }
</style>
