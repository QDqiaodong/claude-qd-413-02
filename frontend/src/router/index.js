import { createRouter, createWebHistory } from 'vue-router'
import Room from '../views/Room.vue'
import Equipment from '../views/Equipment.vue'
import Booking from '../views/Booking.vue'
import Track from '../views/Track.vue'
import MasterRelease from '../views/MasterRelease.vue'

const routes = [
  { path: '/', redirect: '/rooms' },
  { path: '/rooms', name: '录音室', component: Room },
  { path: '/equipments', name: '录音设备', component: Equipment },
  { path: '/bookings', name: '预约', component: Booking },
  { path: '/tracks', name: '曲目', component: Track },
  { path: '/master-releases', name: '母带放行', component: MasterRelease }
]

export default createRouter({ history: createWebHistory(), routes })
