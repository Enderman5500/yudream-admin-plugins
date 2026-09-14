<script setup lang="ts">
import { computed } from 'vue'

/**
 * 邮件 HTML 预览：完全沙箱 iframe + 严格 CSP。
 *
 * 邮件正文来自外部发件人，直接 v-html 会把对方的内容注入后台页面；这里用 sandbox（不允许脚本、
 * 表单、导航、同源）并禁止远程资源加载，避免脚本执行与远程像素回传。
 * 邮件排版通常假设白底，因此预览区固定白底深色字。
 */
const props = defineProps<{ html: string }>()

const BODY_STYLE = "html,body{margin:0;padding:12px;background:#fff;color:#1f2328;"
  + "font:14px/1.7 system-ui,-apple-system,'Segoe UI',sans-serif;word-break:break-word}"
  + 'img{max-width:100%;height:auto}table{max-width:100%}a{color:#2563eb}'

const CSP = "default-src 'none'; img-src data: blob:; style-src 'unsafe-inline'; font-src data:; "
  + "base-uri 'none'; form-action 'none'"

const srcdoc = computed(() => '<!DOCTYPE html><html><head><meta charset="utf-8">'
  + `<meta http-equiv="Content-Security-Policy" content="${CSP}">`
  + `<style>${BODY_STYLE}</style></head><body>${props.html}</body></html>`)
</script>

<template>
  <iframe class="mail-html-preview" :srcdoc="srcdoc" sandbox="" title="邮件正文预览" />
</template>

<style scoped>
.mail-html-preview {
  width: 100%;
  min-height: 340px;
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  background: #fff;
}
</style>
