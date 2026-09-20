<template>
  <div>
    <div class="bar">
      <h3>录音设备</h3>
      <span class="tip">提示：双击「名称」或「类型」单元格即可行内编辑，回车保存；报故障会当场拆掉该房间进行中的预约</span>
    </div>
    <el-table :data="equipments" border stripe>
      <el-table-column prop="code" label="编号" width="110" />
      <el-table-column label="名称" min-width="170">
        <template #default="{ row }">
          <el-input
            v-if="editing.id === row.id && editing.field === 'name'"
            v-model="editing.value" size="small"
            @keyup.enter="commit(row)" @blur="commit(row)" />
          <span v-else class="cell-edit" @dblclick="startEdit(row, 'name')">{{ row.name }}</span>
        </template>
      </el-table-column>
      <el-table-column label="归属室" width="150">
        <template #default="{ row }">{{ roomName(row.roomId) }}</template>
      </el-table-column>
      <el-table-column label="类型" width="130">
        <template #default="{ row }">
          <el-input
            v-if="editing.id === row.id && editing.field === 'type'"
            v-model="editing.value" size="small"
            @keyup.enter="commit(row)" @blur="commit(row)" />
          <span v-else class="cell-edit" @dblclick="startEdit(row, 'type')">{{ row.type }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="130">
        <template #default="{ row }">
          <el-select
            :model-value="row.status"
            size="small"
            @change="(v) => changeStatus(row, v)">
            <el-option label="空闲" value="空闲" />
            <el-option label="占用" value="占用" />
            <el-option label="故障" value="故障" />
          </el-select>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import http from '../api'

const equipments = ref([])
const rooms = ref([])
const editing = ref({ id: null, field: '', value: '' })

function roomName(id) {
  const r = rooms.value.find(x => x.id === id)
  return r ? r.code + ' ' + (r.name || '') : '—'
}
function startEdit(row, field) {
  editing.value = { id: row.id, field, value: row[field] }
}
async function commit(row) {
  const field = editing.value.field
  const value = editing.value.value
  editing.value = { id: null, field: '', value: '' }
  if (!value || value === row[field]) return
  await http.put('/equipments/' + row.id, { [field]: value })
  await load()
}
async function changeStatus(row, status) {
  // 报故障 / 改回：成功失败都重新拉一遍，房间和预约可能已被连带拆掉
  try {
    await http.put('/equipments/' + row.id, { status })
  } finally {
    await load()
  }
}
async function load() {
  ;[equipments.value, rooms.value] = await Promise.all([http.get('/equipments'), http.get('/rooms')])
}
onMounted(load)
</script>

<style scoped>
.bar { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.tip { color: #8a8499; font-size: 13px; }
.cell-edit { display: inline-block; min-height: 22px; padding: 0 4px; cursor: text; border-radius: 4px; }
.cell-edit:hover { background: var(--el-color-primary-light-9); outline: 1px dashed var(--el-color-primary-light-5); }
</style>
