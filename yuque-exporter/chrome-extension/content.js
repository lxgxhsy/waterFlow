/**
 * 语雀纯净提取器 - content.js
 */

(function injectStyles() {
  const style = document.createElement('style');
  style.textContent = `
    *, *::before, *::after {
      -webkit-user-select: text !important;
      user-select: text !important;
    }
    .ne-selection-layer, .ne-mask, .ne-codeblock-lines { display: none !important; }
  `;
  document.documentElement.appendChild(style);
})();

function cleanCodeText(rawText) {
  if (!rawText) return "";
  let code = rawText;
  code = code.replace(/^(\s*\d+\s*\n)+/m, '');
  code = code.replace(/^(99123456789\d*|123456789\d*)/, '');

  let lines = code.split('\n');
  const needsLineClean = lines.length > 1 && lines.slice(0, 3).every(l => /^\s*\d+\s+[a-zA-Z_{}]/.test(l));

  if (needsLineClean) {
    lines = lines.map(line => line.replace(/^\s*\d+\s+/, ''));
  } else {
    const isTightNumbers = lines.length > 1 && lines.slice(0, 3).every(l => /^\d+[a-zA-Z]/.test(l));
    if (isTightNumbers) {
      lines = lines.map(line => line.replace(/^\d+/, ''));
    }
  }
  return lines.join('\n').trim();
}

function extractPureContent() {
  const container = document.querySelector('.ne-viewer-body') ||
                    document.querySelector('.lake-content') ||
                    document.querySelector('article');

  if (!container) return { success: false, message: '未找到内容区域' };

  let markdownOutput = "";
  const elements = container.querySelectorAll('h1, h2, h3, h4, p, li, .ne-codeblock, pre');

  elements.forEach(node => {
    if (node.classList.contains('ne-codeblock') || node.tagName === 'PRE') {
      const rawCode = node.innerText;
      const cleanCode = cleanCodeText(rawCode);
      if (cleanCode) {
        markdownOutput += "```\n" + cleanCode + "\n```\n\n";
      }
    } else if (node.tagName.startsWith('H')) {
      const level = node.tagName.substring(1);
      markdownOutput += "#".repeat(parseInt(level)) + " " + node.innerText.trim() + "\n\n";
    } else if (!node.closest('.ne-codeblock')) {
      const text = node.innerText.trim();
      if (text) markdownOutput += text + "\n\n";
    }
  });

  return {
    success: true,
    data: markdownOutput,
    title: document.title.replace(/\s*-\s*语雀.*$/, ''),
    url: location.href
  };
}

// 收集知识库中所有文档链接（用于批量导出）
function collectDocLinks() {
  const links = new Set();

  // 侧边栏文档树
  document.querySelectorAll('.book-catalog a[href], .catalog-list a[href], .sidebar a[href]').forEach(a => {
    const href = a.getAttribute('href');
    if (href && /\/[^/]+\/[^/]+\/[^/]+/.test(href)) {
      links.add(new URL(href, location.origin).href);
    }
  });

  // 目录页文档卡片
  document.querySelectorAll('a.doc-item, a[class*="doc-card"], a[class*="toc-item"]').forEach(a => {
    const href = a.getAttribute('href');
    if (href) links.add(new URL(href, location.origin).href);
  });

  return Array.from(links);
}

chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
  if (request.action === 'extract') {
    sendResponse(extractPureContent());
  } else if (request.action === 'collectLinks') {
    sendResponse({ success: true, links: collectDocLinks() });
  }
  return true;
});
