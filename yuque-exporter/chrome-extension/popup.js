const statusEl      = document.getElementById('status');
const progressDiv   = document.getElementById('progress');
const progressBar   = document.getElementById('progressBar');
const progressText  = document.getElementById('progressText');
const extractBtn    = document.getElementById('extractBtn');
const batchBtn      = document.getElementById('batchBtn');

// ── 单页提取 ──────────────────────────────────────────────
extractBtn.addEventListener('click', async () => {
  statusEl.textContent = '正在提取内容...';
  try {
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
    if (!tab?.id) { statusEl.textContent = '❌ 无法获取当前标签页'; return; }

    chrome.tabs.sendMessage(tab.id, { action: 'extract' }, async response => {
      if (chrome.runtime.lastError || !response?.success) {
        statusEl.textContent = '❌ ' + (chrome.runtime.lastError?.message || response?.message || '提取失败');
        return;
      }
      try {
        await navigator.clipboard.writeText(response.data);
        statusEl.innerHTML = `✅ 已复制：${response.title}<br>`;
      } catch (e) {
        statusEl.textContent = '❌ 复制失败：' + e.message;
      }
    });
  } catch (e) {
    statusEl.textContent = '❌ 错误：' + e.message;
  }
});

// ── 批量导出 ──────────────────────────────────────────────
batchBtn.addEventListener('click', async () => {
  batchBtn.disabled = true;
  extractBtn.disabled = true;
  progressDiv.style.display = 'block';
  statusEl.textContent = '正在收集文档链接...';

  try {
    const [activeTab] = await chrome.tabs.query({ active: true, currentWindow: true });
    if (!activeTab?.id) throw new Error('无法获取当前标签页');

    // 1. 收集当前页面上的所有文档链接
    const linkRes = await sendMsg(activeTab.id, { action: 'collectLinks' });
    let urls = linkRes?.links || [];

    // 如果侧边栏没有找到链接，把当前页也加进去
    if (urls.length === 0) urls = [activeTab.url];

    setProgress(0, urls.length, '准备中...');

    const collected = [];

    // 2. 逐一打开每个文档提取内容（复用当前标签页）
    for (let i = 0; i < urls.length; i++) {
      const url = urls[i];
      setProgress(i, urls.length, `(${i + 1}/${urls.length}) 正在读取...`);

      try {
        await chrome.tabs.update(activeTab.id, { url });
        await waitForLoad(activeTab.id);
        await sleep(800); // 等待 JS 渲染

        const res = await sendMsg(activeTab.id, { action: 'extract' });
        if (res?.success && res.data) {
          collected.push({ title: res.title || `doc_${i}`, content: res.data, url });
        }
      } catch (e) {
        console.warn('跳过:', url, e.message);
      }
    }

    setProgress(urls.length, urls.length, '正在打包...');

    // 3. 打包成 ZIP 并下载
    const zip = buildZip(collected);
    downloadBlob(zip, 'yuque_export.zip');

    statusEl.innerHTML = `✅ 导出完成，共 ${collected.length} 篇文档`;
  } catch (e) {
    statusEl.textContent = '❌ ' + e.message;
  } finally {
    batchBtn.disabled = false;
    extractBtn.disabled = false;
  }
});

// ── 工具函数 ──────────────────────────────────────────────
function sendMsg(tabId, msg) {
  return new Promise((resolve, reject) => {
    chrome.tabs.sendMessage(tabId, msg, res => {
      if (chrome.runtime.lastError) reject(new Error(chrome.runtime.lastError.message));
      else resolve(res);
    });
  });
}

function waitForLoad(tabId) {
  return new Promise(resolve => {
    const check = (id, info) => {
      if (id === tabId && info.status === 'complete') {
        chrome.tabs.onUpdated.removeListener(check);
        resolve();
      }
    };
    chrome.tabs.onUpdated.addListener(check);
    // timeout fallback
    setTimeout(resolve, 8000);
  });
}

function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

function setProgress(done, total, label) {
  progressBar.max   = total;
  progressBar.value = done;
  progressText.textContent = label;
}

// 极简 ZIP 打包（store，无压缩）
function buildZip(docs) {
  const enc   = new TextEncoder();
  const parts = [];
  const cds   = [];
  let offset  = 0;

  docs.forEach(({ title, content }) => {
    const safe = title.replace(/[/\\?%*:|"<>]/g, '_').slice(0, 80) + '.md';
    const data = enc.encode(content);
    const name = enc.encode(safe);

    // Local file header
    const lhSize = 30 + name.length;
    const lh = new Uint8Array(lhSize);
    const lv = new DataView(lh.buffer);
    lv.setUint32(0, 0x04034b50, true); // signature
    lv.setUint16(4, 20, true);         // version needed
    lv.setUint32(14, crc32(data), true); // crc32
    lv.setUint32(18, data.length, true); // compressed
    lv.setUint32(22, data.length, true); // uncompressed
    lv.setUint16(26, name.length, true);
    lh.set(name, 30);

    parts.push(lh, data);

    // Central directory entry
    const cd = new Uint8Array(46 + name.length);
    const cv = new DataView(cd.buffer);
    cv.setUint32(0, 0x02014b50, true);
    cv.setUint16(4, 20, true);
    cv.setUint16(6, 20, true);
    cv.setUint32(16, crc32(data), true);
    cv.setUint32(20, data.length, true);
    cv.setUint32(24, data.length, true);
    cv.setUint16(28, name.length, true);
    cv.setUint32(42, offset, true);
    cd.set(name, 46);
    cds.push(cd);

    offset += lhSize + data.length;
  });

  const cdBuf = concat(cds);
  const eocd  = new Uint8Array(22);
  const ev    = new DataView(eocd.buffer);
  ev.setUint32(0, 0x06054b50, true);
  ev.setUint16(8, docs.length, true);
  ev.setUint16(10, docs.length, true);
  ev.setUint32(12, cdBuf.length, true);
  ev.setUint32(16, offset, true);

  return concat([...parts, cdBuf, eocd]);
}

function concat(arrays) {
  const total = arrays.reduce((s, a) => s + a.length, 0);
  const out   = new Uint8Array(total);
  let pos     = 0;
  arrays.forEach(a => { out.set(a, pos); pos += a.length; });
  return out;
}

function crc32(buf) {
  let crc = 0xFFFFFFFF;
  for (let i = 0; i < buf.length; i++) {
    crc ^= buf[i];
    for (let j = 0; j < 8; j++) crc = (crc >>> 1) ^ (crc & 1 ? 0xEDB88320 : 0);
  }
  return (crc ^ 0xFFFFFFFF) >>> 0;
}

function downloadBlob(data, filename) {
  const url = URL.createObjectURL(new Blob([data], { type: 'application/zip' }));
  const a   = Object.assign(document.createElement('a'), { href: url, download: filename });
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
