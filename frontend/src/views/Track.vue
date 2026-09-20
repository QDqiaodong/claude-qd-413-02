<template>
  <div>
    <div class="bar"><h3>曲目评分</h3></div>
    <el-table :data="tracks" border stripe>
      <el-table-column label="归属预约" width="150">
        <template #default="{ row }">{{ bookingName(row.bookingId) }}</template>
      </el-table-column>
      <el-table-column prop="name" label="曲名" min-width="160" />
      <el-table-column label="时长(分)" width="100" prop="duration" />
      <el-table-column label="评分" width="240">
        <template #default="{ row }">
          <el-rate
            :model-value="row.rating || 0"
            @change="(v) => rate(row, v)"
            clearable
            show-score />
          <el-button v-if="row.rating" link type="danger" size="small" @click="rate(row, 0)">清星</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import http from '../api'

const tracks = ref([])
const bookings = ref([])

function bookingName(id) {
  const b = bookings.value.find(x => x.id === id)
  return b ? b.custName + '（' + (b.roomId ?? '') + '）' : '—'
}
function rate(row, v) {
  return http.put('/tracks/' + row.id, { rating: v }).then(load)
}
async function load() {
  ;[tracks.value, bookings.value] = await Promise.all([http.get('/tracks'), http.get('/bookings')])
}
onMounted(load)
</script>

<style scoped>
.bar { margin-bottom: 12px; }
</style>
