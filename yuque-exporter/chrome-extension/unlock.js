/**
 * 运行在 MAIN 环境，直接修改原型链
 */
(function() {
    const blockedEvents = ['copy', 'cut', 'selectstart', 'contextmenu', 'dragstart'];
    const originalAddEventListener = EventTarget.prototype.addEventListener;

    // 劫持 addEventListener
    EventTarget.prototype.addEventListener = function(type, listener, options) {
      if (blockedEvents.includes(type.toLowerCase())) {
        console.log('[语雀助手] 已拦截限制事件:', type);
        return;
      }
      return originalAddEventListener.call(this, type, listener, options);
    };

    // 定期清理内联处理程序
    const clear = () => {
      blockedEvents.forEach(evt => {
        document['on' + evt] = null;
        if (document.body) document.body['on' + evt] = null;
      });
    };
    setInterval(clear, 2000);
  })();
