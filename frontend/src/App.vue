<template>
  <el-container class="app" data-v-app>
    <el-header class="topbar">
      <div class="brand">🎙 录音棚管理系统</div>
    </el-header>
    <el-container class="body">
      <nav class="rail">
        <div
          v-for="m in modules"
          :key="m.path"
          class="rail-item"
          :class="{ active: route.path === m.path }"
          @click="$router.push(m.path)"
        >
          <span class="ico">{{ m.icon }}</span>
          <span class="lbl">{{ m.label }}</span>
        </div>
      </nav>
      <el-main class="main"><router-view /></el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { useRoute } from 'vue-router'

const route = useRoute()
const modules = [
  { path: '/rooms', label: '录音室', icon: '🎚' },
  { path: '/equipments', label: '录音设备', icon: '🎛' },
  { path: '/bookings', label: '预约', icon: '📅' },
  { path: '/tracks', label: '曲目', icon: '🎵' },
  { path: '/master-releases', label: '母带放行', icon: '🟢' }
]
</script>

<style>
html, body, #app { margin: 0; height: 100%; }
.app { height: 100vh; }
.topbar {
  display: flex; align-items: center; justify-content: center;
  background: var(--el-color-primary); color: #fff; font-weight: 700; font-size: 18px;
  box-shadow: 0 2px 6px rgba(0,0,0,.12);
}
.body { display: flex; flex: 1; min-height: 0; }
.rail {
  width: 64px; background: #2b2440; display: flex; flex-direction: column;
  align-items: stretch; padding-top: 12px; flex-shrink: 0;
}
.rail-item {
  position: relative; height: 60px; display: flex; align-items: center; justify-content: center;
  color: #cfc6e6; cursor: pointer; font-size: 22px;
}
.rail-item:hover, .rail-item.active { background: rgba(255,255,255,0.12); color: #fff; }
.rail-item .lbl {
  position: absolute; left: 64px; top: 0; height: 60px; line-height: 60px;
  background: #2b2440; padding: 0 14px; white-space: nowrap; opacity: 0; pointer-events: none;
  transition: opacity .15s; border-radius: 0 6px 6px 0; z-index: 20; font-size: 14px;
}
.rail-item:hover .lbl { opacity: 1; }
.main { background: #f7f5fb; padding: 20px; overflow: auto; }
</style>
