<template>
  <div>
    <CircularLoading v-if="loading" :show="loading"/>
    <div v-else-if="localConfiguration">
      <div v-if="localConfiguration.message" class="p-4">
        <Alert :type="'error' as any" class="overflow-x-auto" :title="localConfiguration.message"/>
      </div>
      <div v-else>
        <VisualTable v-if="configuration?.type === Type.TABLE" :configuration="localConfiguration" :submitted="false" :width="width" :height="height"/>
        <VisualLine v-else-if="configuration?.type === Type.LINE" :configuration="localConfiguration" :submitted="false" :width="width" :height="height"/>
        <VisualBar v-else-if="configuration?.type === Type.BAR" :configuration="localConfiguration" :submitted="false" :width="width" :height="height"/>
        <VisualArea v-else-if="configuration?.type === Type.AREA" :configuration="localConfiguration" :submitted="false" :width="width" :height="height"/>
        <VisualPie v-else-if="configuration?.type === Type.PIE" :configuration="localConfiguration" :submitted="false" :width="width" :height="height"/>
        <VisualHistogram v-else-if="configuration?.type === Type.HISTOGRAM" :configuration="localConfiguration" :submitted="false" :width="width" :height="height"/>
        <VisualWordCloud v-else-if="configuration?.type === Type.WORDCLOUD" :configuration="localConfiguration" :submitted="false" :width="width" :height="height"/>
        <VisualScatter v-else-if="configuration?.type === Type.SCATTER" :configuration="localConfiguration" :submitted="false" :width="width" :height="height"/>
        <VisualRadar v-else-if="configuration?.type === Type.RADAR" :configuration="localConfiguration" :submitted="false" :width="width" :height="height"/>
        <VisualFunnel v-else-if="configuration?.type === Type.FUNNEL" :configuration="localConfiguration" :submitted="false" :width="width" :height="height"/>
        <VisualGauge v-else-if="configuration?.type === Type.GAUGE" :configuration="localConfiguration" :submitted="false" :width="width" :height="height"/>
        <VisualRose v-else-if="configuration?.type === Type.ROSE" :configuration="localConfiguration" :submitted="false" :width="width" :height="height"/>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { cloneDeep } from 'lodash'
import { Type } from '@/views/components/visual/Type'
import VisualWordCloud from '@/views/components/visual/components/VisualWordCloud.vue'
import VisualHistogram from '@/views/components/visual/components/VisualHistogram.vue'
import VisualPie from '@/views/components/visual/components/VisualPie.vue'
import VisualArea from '@/views/components/visual/components/VisualArea.vue'
import { Configuration } from './Configuration'
import VisualBar from '@/views/components/visual/components/VisualBar.vue'
import VisualLine from '@/views/components/visual/components/VisualLine.vue'
import VisualTable from '@/views/components/visual/components/VisualTable.vue'
import { ToastUtils } from '@/utils/toast'
import DatasetService from '@/services/dataset'
import CircularLoading from '@/views/components/loading/CircularLoading.vue'
import { defineComponent } from 'vue'
import VisualRadar from '@/views/components/visual/components/VisualRadar.vue'
import VisualScatter from '@/views/components/visual/components/VisualScatter.vue'
import VisualFunnel from '@/views/components/visual/components/VisualFunnel.vue'
import VisualGauge from '@/views/components/visual/components/VisualGauge.vue'
import VisualRose from '@/views/components/visual/components/VisualRose.vue'
import Alert from '@/views/ui/alert'
import ExecuteService from '@/services/execute.ts'
import { ExecuteModel } from '@/model/execute.ts'

export default defineComponent({
  name: 'VisualView',
  computed: {
    Type()
    {
      return Type
    }
  },
  components: {
    Alert,
    VisualRose,
    VisualGauge,
    VisualFunnel,
    VisualScatter,
    VisualRadar,
    CircularLoading,
    VisualWordCloud, VisualHistogram, VisualPie, VisualArea, VisualBar, VisualLine, VisualTable
  },
  props: {
    configuration: {
      type: Object as () => Configuration | null
    },
    query: {
      type: Object
    },
    code: {
      type: String
    },
    type: {
      type: String
    },
    width: {
      type: String,
      default: () => '100%'
    },
    height: {
      type: String,
      default: () => '400px'
    },
    original: {
      type: Number
    }
  },
  data()
  {
    return {
      loading: false,
      localConfiguration: null as Configuration | null
    }
  },
  created()
  {
    this.handlerInitialize()
  },
  methods: {
    handlerInitialize()
    {
      this.localConfiguration = cloneDeep(this.configuration) as Configuration
      setTimeout(() => {
        this.loading = true
        if (this.type === 'QUERY') {
          const configure: ExecuteModel = { name: this.original as any, content: this.query as any, mode: 'REPORT', format: 'JSON' }
          ExecuteService.execute(configure, null)
                        .then(response => {
                          if (response.data.isSuccessful) {
                            this.formatRaw(response)
                          }
                          else {
                            ToastUtils.error(response.data.message)
                          }
                        })
                        .finally(() => this.loading = false)
        }
        else {
          DatasetService.adhoc(this.code!, this.query)
                        .then(response => {
                          if (response.status) {
                            this.formatRaw(response)
                          }
                          else {
                            ToastUtils.error(response.message)
                          }
                        })
                        .finally(() => this.loading = false)
        }
      })
    },
    formatRaw(response: any)
    {
      if (this.localConfiguration) {
        if (response.data.isSuccessful) {
          this.localConfiguration.headers = response.data.headers
          this.localConfiguration.columns = response.data.columns
          this.localConfiguration.message = null
        }
        else {
          this.localConfiguration.headers = []
          this.localConfiguration.columns = []
          this.localConfiguration.message = response.data.message
        }
      }
    }
  }
})
</script>
