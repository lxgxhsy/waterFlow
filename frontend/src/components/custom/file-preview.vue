<template>
  <div class="file-preview-container">
    <!-- 预览头部 -->
    <div class="preview-header">
      <div class="flex items-center gap-2">
        <SvgIcon :local-icon="getFileIcon(fileName)" class="text-16" />
        <span class="font-medium">{{ fileName }}</span>
      </div>
      <div class="flex items-center gap-2">
        <NButton size="small" @click="downloadFile" :loading="downloading">
          <template #icon>
            <icon-mdi-download />
          </template>
          下载
        </NButton>
        <NButton size="small" @click="closePreview">
          <template #icon>
            <icon-mdi-close />
          </template>
        </NButton>
      </div>
    </div>
    
    <!-- 预览内容 -->
    <div class="preview-content">
      <template v-if="loading">
        <div class="flex items-center justify-center h-full">
          <NSpin size="large" />
        </div>
      </template>
      <template v-else-if="error">
        <div class="flex flex-col items-center justify-center h-full text-gray-500">
          <icon-mdi-alert-circle class="text-48 mb-4" />
          <p>{{ error }}</p>
        </div>
      </template>
      <template v-else-if="previewHint">
        <div class="preview-placeholder">
          <icon-mdi-file-alert class="text-48 mb-4" />
          <p class="preview-placeholder-title">{{ previewHint.title }}</p>
          <p class="preview-placeholder-desc">{{ previewHint.description }}</p>
          <NButton type="primary" ghost size="small" :loading="downloading" @click="downloadFile">
            <template #icon>
              <icon-mdi-download />
            </template>
            下载文件
          </NButton>
        </div>
      </template>
      <template v-else>
        <div class="content-wrapper">
          <pre class="preview-text">{{ content }}</pre>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { NButton, NSpin } from 'naive-ui';
import SvgIcon from '@/components/custom/svg-icon.vue';
import { request } from '@/service/request';
import { getFileExt } from '@/utils/common';

interface Props {
  fileName: string;
  visible: boolean;
}

interface Emits {
  (e: 'close'): void;
}

const props = defineProps<Props>();
const emit = defineEmits<Emits>();

const loading = ref(false);
const downloading = ref(false);
const content = ref('');
const error = ref('');

const textPreviewExtensions = [
  'txt',
  'md',
  'html',
  'htm',
  'xml',
  'json',
  'csv',
  'log',
  'java',
  'js',
  'ts',
  'py',
  'cpp',
  'c',
  'h',
  'css',
  'scss',
  'less',
  'sql',
  'yml',
  'yaml',
  'properties',
  'conf',
  'config'
];

const unsupportedPreviewExtensions = ['pdf', 'doc', 'docx'];

const fileExtension = computed(() => (getFileExt(props.fileName) || '').toLowerCase());

const previewHint = computed(() => {
  if (!props.fileName || loading.value || error.value) return null;

  if (unsupportedPreviewExtensions.includes(fileExtension.value)) {
    return {
      title: '暂不支持在线预览',
      description: '当前文件类型无法稳定解析为可预览内容，请下载到本地查看。'
    };
  }

  if (!textPreviewExtensions.includes(fileExtension.value)) {
    return {
      title: '暂不支持该文件类型预览',
      description: '当前仅支持常见文本和代码文件在线预览，请下载到本地查看。'
    };
  }

  if (!content.value.trim()) {
    return {
      title: '暂无可显示的预览内容',
      description: '文件内容为空，或服务端没有解析出可展示的文本。'
    };
  }

  return null;
});

// 获取文件图标
function getFileIcon(fileName: string) {
  const ext = getFileExt(fileName);
  if (ext) {
    const supportedIcons = ['pdf', 'doc', 'docx', 'txt', 'md', 'jpg', 'jpeg', 'png', 'gif'];
    return supportedIcons.includes(ext.toLowerCase()) ? ext : 'dflt';
  }
  return 'dflt';
}

// 监听文件名变化，加载预览内容
watch(() => props.fileName, async (newFileName) => {
  if (newFileName && props.visible) {
    await loadPreviewContent();
  }
}, { immediate: true });

// 监听可见性变化
watch(() => props.visible, async (visible) => {
  if (visible && props.fileName) {
    await loadPreviewContent();
  }
});

// 加载预览内容
async function loadPreviewContent() {
  if (!props.fileName) return;
  
  loading.value = true;
  error.value = '';
  content.value = '';

  if (!textPreviewExtensions.includes(fileExtension.value)) {
    loading.value = false;
    return;
  }
  
  try {
    const token = localStorage.getItem('token');
    const { error: requestError, data } = await request<{
      fileName: string;
      content: string;
      fileSize: number;
    }>({
      url: '/documents/preview',
      params: {
        fileName: props.fileName,
        token: token || undefined
      }
    });
    
    if (requestError) {
      error.value = '预览失败：' + (requestError.message || '未知错误');
    } else if (data) {
      content.value = data.content || '';
    }
  } catch (err: any) {
    error.value = '预览失败：' + (err.message || '网络错误');
  } finally {
    loading.value = false;
  }
}

// 下载文件
async function downloadFile() {
  if (!props.fileName) return;
  
  downloading.value = true;
  
  try {
    const token = localStorage.getItem('token');
    const { error: requestError, data } = await request<{
      fileName: string;
      downloadUrl: string;
      fileSize: number;
    }>({
      url: '/documents/download',
      params: {
        fileName: props.fileName,
        token: token || undefined
      }
    });
    
    if (requestError) {
      window.$message?.error('下载失败：' + (requestError.message || '未知错误'));
    } else if (data) {
      // 使用预签名URL下载文件
      const link = document.createElement('a');
      link.href = data.downloadUrl;
      link.download = data.fileName;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.$message?.success('开始下载文件');
    }
  } catch (err: any) {
    window.$message?.error('下载失败：' + (err.message || '网络错误'));
  } finally {
    downloading.value = false;
  }
}

// 关闭预览
function closePreview() {
  emit('close');
}
</script>

<style scoped lang="scss">
.file-preview-container {
  @apply h-full flex flex-col;
  color: var(--n-text-color);
  background-color: var(--n-color);
  border-left: 1px solid var(--n-border-color);
  
  .preview-header {
    @apply flex items-center justify-between p-4;
    color: var(--n-text-color);
    background-color: var(--n-action-color);
    border-bottom: 1px solid var(--n-border-color);
  }
  
  .preview-content {
    @apply flex-1 overflow-hidden;
    min-height: 560px;
    
    .content-wrapper {
      @apply h-full overflow-auto p-4;
      background-color: var(--n-color);
    }
    
    .preview-text {
      @apply text-sm font-mono whitespace-pre-wrap break-words;
      color: var(--n-text-color);
      font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
      line-height: 1.5;
      margin: 0;
    }

    .preview-placeholder {
      @apply h-full flex flex-col items-center justify-center gap-3 px-6 text-center;
      color: var(--n-text-color-3);
    }

    .preview-placeholder-title {
      @apply m-0 text-base font-medium;
      color: var(--n-text-color);
    }

    .preview-placeholder-desc {
      @apply m-0 max-w-520px text-sm leading-6;
      color: var(--n-text-color-3);
    }
  }
}
</style>
