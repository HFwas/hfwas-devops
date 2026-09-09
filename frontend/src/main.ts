import { createApp } from 'vue'
import { createPinia } from 'pinia'
import {
  NAlert, NAvatar, NBadge,
  NButton, NButtonGroup,
  NCard, NCheckbox, NCheckboxGroup,
  NCollapse, NCollapseItem, NColorPicker,
  NConfigProvider,
  NDataTable, NDatePicker,
  NDescriptions, NDescriptionsItem, NDialogProvider,
  NDivider, NDrawer, NDrawerContent, NDropdown,
  NEllipsis, NEmpty,
  NForm, NFormItem,
  NGi, NGrid, NGridItem,
  NIcon, NInput, NInputNumber,
  NLayout, NLayoutContent, NLayoutHeader, NLayoutSider,
  NLi, NList, NListItem, NLog,
  NMenu, NMessageProvider, NModal,
  NPageHeader, NPagination, NPopconfirm, NPopover, NProgress,
  NRadio, NRadioButton, NRadioGroup, NResult,
  NScrollbar, NSelect, NSkeleton, NSpace, NSpin, NStatistic, NSwitch,
  NTable, NTabPane, NTabs, NTag, NText, NThing,
  NTimeline, NTimelineItem, NTree, NTreeSelect, NTooltip,
  NUl, NUpload,
} from 'naive-ui'
import App from './App.vue'
import router from './router'
import { initKeycloak } from '@/shared/keycloak'

const naiveComponents = [
  NAlert, NAvatar, NBadge,
  NButton, NButtonGroup,
  NCard, NCheckbox, NCheckboxGroup,
  NCollapse, NCollapseItem, NColorPicker,
  NConfigProvider,
  NDataTable, NDatePicker,
  NDescriptions, NDescriptionsItem, NDialogProvider,
  NDivider, NDrawer, NDrawerContent, NDropdown,
  NEllipsis, NEmpty,
  NForm, NFormItem,
  NGi, NGrid, NGridItem,
  NIcon, NInput, NInputNumber,
  NLayout, NLayoutContent, NLayoutHeader, NLayoutSider,
  NLi, NList, NListItem, NLog,
  NMenu, NMessageProvider, NModal,
  NPageHeader, NPagination, NPopconfirm, NPopover, NProgress,
  NRadio, NRadioButton, NRadioGroup, NResult,
  NScrollbar, NSelect, NSkeleton, NSpace, NSpin, NStatistic, NSwitch,
  NTable, NTabPane, NTabs, NTag, NText, NThing,
  NTimeline, NTimelineItem, NTree, NTreeSelect, NTooltip,
  NUl, NUpload,
]

async function bootstrap() {
  await initKeycloak()
  const app = createApp(App)
  app.use(createPinia())
  app.use(router)
  naiveComponents.forEach(c => app.component((c as any).name ?? '', c))
  app.mount('#app')
}

void bootstrap()
