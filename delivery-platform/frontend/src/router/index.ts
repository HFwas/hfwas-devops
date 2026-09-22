import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/delivery',
    component: () => import('../layouts/DeliveryShell.vue'),
    redirect: '/delivery/products',
    children: [
      {
        path: 'clusters',
        name: 'clusters',
        component: () => import('../views/delivery/ClusterList.vue'),
      },
      {
        path: 'products',
        name: 'products',
        component: () => import('../views/delivery/ProductList.vue'),
      },
      {
        path: 'products/:id',
        name: 'product-detail',
        component: () => import('../views/delivery/ProductDetail.vue'),
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/delivery/products',
  },
]

export default createRouter({
  history: createWebHistory(),
  routes,
})