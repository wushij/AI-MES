import { marked } from 'marked'

marked.setOptions({
  breaks: true,
  gfm: true
})

/** 将 AI 回复中的单换行渲染为 <br>，避免「标题：」与「🟢 已完成：」挤在同一行 */
export function renderChatMarkdown(text: string) {
  return marked.parse(text) as string
}
