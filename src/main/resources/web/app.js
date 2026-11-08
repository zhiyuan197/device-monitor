"use strict";

const $ = (id) => document.getElementById(id);

const state = {
  devices: new Map(),
  alerts: [],
  connected: false,
  server: { host: "--", port: 0 },
  startedAt: Date.now(),
};

const COLORS = {
  blue: "#3b82f6",
  cyan: "#22d3ee",
  green: "#34d399",
  amber: "#fbbf24",
  red: "#f87171",
  muted: "#6d7f9e",
};

let trendChart = null;
let topoChart = null;
let trendObserved = false;
let topoObserved = false;
let retryDelay = 1000;
let renderedAlertsKey = null;
let renderedHeatKey = null;
let lastDeviceRender = 0;
let lastAlertIds = new Set();

function latencyColor(ms) {
  if (ms < 50) return COLORS.green;
  if (ms < 150) return COLORS.amber;
  return COLORS.red;
}

function latencyLabel(ms) {
  if (ms < 50) return "链路状态良好";
  if (ms < 150) return "链路存在波动";
  return "链路延迟偏高";
}

function pad(n) {
  return String(n).padStart(2, "0");
}

function fmtClock() {
  const now = new Date();
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}  ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`;
}

function fmtUptime(startedAt) {
  const seconds = Math.max(0, Math.floor((Date.now() - startedAt) / 1000));
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  return `${pad(h)}:${pad(m)}:${pad(s)}`;
}

function countUp(target, value) {
  const start = parseInt(target.textContent.replace(/[^0-9]/g, "") || "0", 10);
  const goal = parseInt(value, 10);
  if (start === goal) {
    target.textContent = goal;
    return;
  }
  const begin = performance.now();
  const duration = 450;
  function step(now) {
    const t = Math.min(1, (now - begin) / duration);
    const eased = 1 - Math.pow(1 - t, 3);
    target.textContent = Math.round(start + (goal - start) * eased);
    if (t < 1) requestAnimationFrame(step);
  }
  requestAnimationFrame(step);
}

function deviceName(id) {
  const device = state.devices.get(id);
  if (!device) return id;
  return device.real ? `${device.name} · 本机` : device.name;
}

function renderStats(snapshot) {
  const stats = snapshot.stats || {};
  countUp($("statOnline"), stats.online || 0);
  $("statTotal").textContent = `共 ${stats.total || 0} 台`;
  countUp($("statAlerts"), stats.activeAlerts || 0);
  $("statAlertsSub").textContent = (stats.activeAlerts || 0) > 0 ? "需关注" : "全部已处理";
  countUp($("statLatency"), stats.avgLatencyMs || 0);
  $("statLatencySub").textContent = latencyLabel(stats.avgLatencyMs || 0);
  $("statUptime").textContent = fmtUptime(snapshot.startedAt || state.startedAt);
  $("statMode").textContent = snapshot.connected ? "实时模式" : "离线缓存";
}

function severityClass(severity) {
  return String(severity || "").toLowerCase().startsWith("crit") ? "critical" : "warn";
}

function alertRuleText(alert) {
  const type = String(alert.ruleType || "");
  if (type === "OFFLINE") return "设备离线";
  if (type === "SERVICE_DOWN") return "服务不可达";
  const value = alert.triggerValue;
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return type + " 阈值触发";
  }
  return `${type} 阈值触发 · ${Number(value).toFixed(1)}`;
}

function renderAlerts() {
  const list = $("alertList");
  const newIds = new Set(state.alerts.map((alert) => alert.id));
  list.textContent = "";
  if (state.alerts.length === 0) {
    const empty = document.createElement("div");
    empty.className = "alert-empty";
    empty.textContent = "暂无活跃告警";
    list.appendChild(empty);
    return;
  }
  for (const alert of state.alerts.slice(0, 12)) {
    const item = document.createElement("div");
    item.className = "alert-item " + severityClass(alert.severity);
    if (!lastAlertIds.has(alert.id)) {
      item.classList.add("new");
    }
    const head = document.createElement("div");
    head.className = "alert-head";
    const device = document.createElement("span");
    device.className = "alert-device";
    device.textContent = deviceName(alert.deviceId);
    const time = document.createElement("span");
    time.className = "alert-time";
    time.textContent = new Date(alert.triggeredAt).toLocaleTimeString("zh-CN", { hour12: false });
    head.append(device, time);
    const rule = document.createElement("div");
    rule.className = "alert-rule";
    rule.textContent = alertRuleText(alert);
    item.append(head, rule);
    list.appendChild(item);
  }
  lastAlertIds = newIds;
}

function alertsKey(alerts) {
  return alerts
      .map((alert) => `${alert.id}:${alert.status}:${alert.triggeredAt}`)
      .join("|");
}

function heatKey() {
  return [...state.devices.values()]
      .sort((a, b) => a.id.localeCompare(b.id))
      .map((d) => `${d.id}:${d.online ? 1 : 0}:${d.online ? d.latencyMs || 0 : 0}`)
      .join("|");
}

function flashAlertsPanel() {
  const panel = $("alerts");
  panel.classList.remove("flash");
  void panel.offsetWidth;
  panel.classList.add("flash");
  setTimeout(() => panel.classList.remove("flash"), 1200);
}

function nodeXY(nodes, id) {
  const node = nodes.find((n) => n.id === id);
  return [node.x, node.y];
}

function nodeColor(node) {
  if (node.kind === "server") return COLORS.blue;
  if (node.kind === "real") return COLORS.cyan;
  return node.online === false ? COLORS.red : COLORS.green;
}

function buildTopology() {
  const devices = [...state.devices.values()];
  const center = { x: 50, y: 46 };
  const real = devices.find((d) => d.real);
  const nodes = [
    { id: "SERVER", name: "监控服务器", x: center.x, y: center.y, kind: "server" },
  ];
  if (real) {
    nodes.push({
      id: real.id,
      name: deviceName(real.id),
      x: 50,
      y: 84,
      kind: "real",
      online: real.online,
    });
  }
  const others = devices.filter((d) => !d.real);
  const links = [];
  others.forEach((device, index) => {
    const count = Math.max(1, others.length);
    const spread = others.length === 1 ? 0.5 : index / (count - 1);
    const angle = Math.PI * (1 - spread);
    const radiusX = 42;
    const radiusY = 26;
    const x = center.x + radiusX * Math.cos(angle);
    const y = center.y - radiusY * Math.sin(angle) - 2;
    nodes.push({
      id: device.id,
      name: deviceName(device.id),
      x,
      y,
      kind: "device",
      online: device.online,
      labelAbove: index % 2 === 0,
    });
    links.push({
      from: "SERVER",
      to: device.id,
      online: device.online,
      latencyMs: device.latencyMs || 0,
    });
  });
  if (real) {
    links.push({
      from: "SERVER",
      to: real.id,
      online: real.online,
      latencyMs: real.latencyMs || 0,
    });
  }
  return { nodes, links };
}

function renderTopology() {
  const { nodes, links } = buildTopology();
  const scatterData = nodes.map((node) => ({
    name: node.name,
    value: [node.x, node.y],
    itemStyle: { color: nodeColor(node) },
    symbolSize: node.kind === "server" ? 18 : node.kind === "real" ? 15 : 12,
    label: {
      show: true,
      position: node.labelAbove ? "top" : "bottom",
      distance: 5,
      color: node.online === false ? COLORS.red : "#9fb2d2",
      fontSize: 9,
    },
    tooltip: { show: true, formatter: node.name },
  }));
  const linesData = links.map((link) => ({
    coords: [nodeXY(nodes, link.from), nodeXY(nodes, link.to)],
    lineStyle: {
      color: link.online === false ? COLORS.red : latencyColor(link.latencyMs),
      width: 1.2,
      opacity: 0.75,
    },
  }));
  const option = {
    animation: true,
    grid: { left: 0, right: 0, top: 0, bottom: 0 },
    xAxis: { min: 0, max: 100, show: false },
    yAxis: { min: 0, max: 100, show: false },
    series: [
      {
        type: "lines",
        coordinateSystem: "cartesian2d",
        data: linesData,
        effect: {
          show: true,
          period: 4.5,
          trailLength: 0.45,
          symbol: "arrow",
          symbolSize: 4,
          color: "#7dd3fc",
        },
        z: 2,
      },
      {
        type: "effectScatter",
        coordinateSystem: "cartesian2d",
        data: scatterData,
        labelLayout: { hideOverlap: true },
        rippleEffect: { period: 3, scale: 2.4, brushType: "stroke" },
        z: 3,
      },
    ],
  };
  if (!topoChart) {
    topoChart = echarts.init($("topoChart"));
    topoObserved = false;
  }
  if (!topoObserved) {
    observeChartResize(topoChart, $("topoChart"));
    topoObserved = true;
  }
  topoChart.setOption(option);
  topoChart.resize();
}

function renderTrends() {
  const online = [...state.devices.values()].filter((d) => d.online);
  const buffers = [];
  for (const device of online) {
    const buffer = (state.trends && state.trends[device.id]) || null;
    if (buffer && Array.isArray(buffer.cpu) && buffer.cpu.length) {
      buffers.push(buffer);
    }
  }
  const maxLen = buffers.length ? Math.max(...buffers.map((b) => b.ts.length)) : 0;
  const avg = (list, idx) => {
    let sum = 0;
    let count = 0;
    for (const b of buffers) {
      const v = list(b)[idx];
      if (typeof v === "number") {
        sum += v;
        count++;
      }
    }
    return count ? +(sum / count).toFixed(1) : null;
  };
  const ts = [];
  const cpu = [];
  const memory = [];
  const net = [];
  for (let i = 0; i < maxLen; i++) {
    ts.push(new Date((buffers[0] && buffers[0].ts[i]) || Date.now())
        .toLocaleTimeString("zh-CN", { hour12: false }));
    cpu.push(avg((b) => b.cpu, i));
    memory.push(avg((b) => b.memory, i));
    net.push(avg((b) => b.networkKbps, i));
  }
  const option = {
    grid: { left: 36, right: 44, top: 26, bottom: 24 },
    tooltip: {
      trigger: "axis",
      backgroundColor: "#0d1728",
      borderColor: "#223252",
      textStyle: { color: "#dbe7ff", fontSize: 11 },
    },
    legend: {
      show: true,
      textStyle: { color: "#6d7f9e", fontSize: 10 },
      top: 0,
      right: 0,
      itemWidth: 12,
      itemHeight: 8,
    },
    xAxis: {
      type: "category",
      data: ts,
      axisLine: { lineStyle: { color: "#223252" } },
      axisLabel: {
        color: "#4c607f",
        fontSize: 9,
        interval: Math.max(0, Math.floor(maxLen / 4) - 1),
      },
    },
    yAxis: [
      {
        type: "value",
        min: 0,
        max: 100,
        splitLine: { lineStyle: { color: "#15213b" } },
        axisLabel: { color: "#4c607f", fontSize: 9 },
      },
      {
        type: "value",
        splitLine: { show: false },
        axisLabel: { color: "#4c607f", fontSize: 9 },
      },
    ],
    series: [
      {
        name: "CPU %",
        type: "line",
        data: cpu,
        smooth: 0.35,
        showSymbol: false,
        lineStyle: { width: 1.5, color: COLORS.blue },
        areaStyle: { color: "rgba(59,130,246,0.14)" },
      },
      {
        name: "内存 %",
        type: "line",
        data: memory,
        smooth: 0.35,
        showSymbol: false,
        lineStyle: { width: 1.5, color: COLORS.cyan },
      },
      {
        name: "网络 Kbps",
        type: "line",
        yAxisIndex: 1,
        data: net,
        smooth: 0.35,
        showSymbol: false,
        lineStyle: { width: 1.5, color: COLORS.amber },
      },
    ],
  };
  if (!trendChart) {
    trendChart = echarts.init($("trendChart"));
    trendObserved = false;
  }
  if (!trendObserved) {
    observeChartResize(trendChart, $("trendChart"));
    trendObserved = true;
  }
  trendChart.setOption(option);
  trendChart.resize();
}

function renderHeat() {
  const list = $("heatList");
  list.textContent = "";
  const devices = [...state.devices.values()].sort((a, b) => a.id.localeCompare(b.id));
  for (const device of devices) {
    const ms = device.online ? device.latencyMs || 0 : null;
    const row = document.createElement("div");
    row.className = "heat-row";
    const name = document.createElement("span");
    name.className = "heat-name";
    name.textContent = deviceName(device.id);
    const track = document.createElement("div");
    track.className = "heat-track";
    const fill = document.createElement("div");
    fill.className = "heat-fill";
    if (ms === null) {
      fill.style.width = "100%";
      fill.style.background = "#26354f";
    } else {
      fill.style.width = Math.min(100, Math.max(6, ms / 4)) + "%";
      fill.style.background = latencyColor(ms);
    }
    track.appendChild(fill);
    const value = document.createElement("span");
    value.className = "heat-value";
    value.textContent = ms === null ? "离线" : `${ms} ms`;
    row.append(name, track, value);
    list.appendChild(row);
  }
}

function renderSnapshot(snapshot) {
  state.connected = snapshot.connected !== false;
  state.startedAt = snapshot.startedAt || state.startedAt;
  state.server = snapshot.server || state.server;
  state.devices = new Map((snapshot.devices || []).map((d) => [d.id, d]));
  state.trends = snapshot.trends || {};
  const incoming = (snapshot.alerts || []).filter((a) => a.status === "ACTIVE");
  const alertsSignature = alertsKey(incoming);
  if (alertsSignature !== renderedAlertsKey) {
    renderedAlertsKey = alertsSignature;
    state.alerts = incoming;
    renderAlerts();
  }
  renderStats(snapshot);
  renderTopology();
  renderTrends();
  const heatSignature = heatKey();
  if (heatSignature !== renderedHeatKey) {
    renderedHeatKey = heatSignature;
    renderHeat();
  }
  $("topoTag").textContent =
      `${state.devices.size} 节点 · ${[...state.devices.values()].filter((d) => d.online).length} 在线`;
  $("serverInfo").textContent =
      `${state.server.host || "--"}:${state.server.port || "--"} · WebSocket 推送`;
}

function handleEvent(message) {
  if (message.type === "alert") {
    const alert = message.alert;
    state.alerts = [alert, ...state.alerts.filter((a) => a.id !== alert.id)].slice(0, 50);
    renderedAlertsKey = alertsKey(state.alerts);
    renderAlerts();
    flashAlertsPanel();
    return;
  }
  if (message.type === "device") {
    state.devices.set(message.device.id, message.device);
    const now = Date.now();
    if (now - lastDeviceRender >= 1000) {
      lastDeviceRender = now;
      renderTopology();
      renderHeat();
    }
    $("topoTag").textContent =
        `${state.devices.size} 节点 · ${[...state.devices.values()].filter((d) => d.online).length} 在线`;
    return;
  }
  if (message.type === "status") {
    state.connected = message.status.connected !== false;
    const conn = $("connState");
    conn.classList.toggle("ok", state.connected);
    conn.classList.toggle("bad", !state.connected);
    conn.innerHTML = state.connected
      ? '<span class="dot green"></span>已连接'
      : '<span class="dot red"></span>连接中断';
  }
}

function connect() {
  const protocol = location.protocol === "https:" ? "wss" : "ws";
  const ws = new WebSocket(`${protocol}://${location.host}/ws`);
  ws.onopen = () => {
    retryDelay = 1000;
    const conn = $("connState");
    conn.classList.add("ok");
    conn.classList.remove("bad");
    conn.innerHTML = '<span class="dot green"></span>已连接';
  };
  ws.onmessage = (event) => {
    let message;
    try {
      message = JSON.parse(event.data);
    } catch (error) {
      return;
    }
    if (message.type === "snapshot") {
      renderSnapshot(message);
    } else {
      handleEvent(message);
    }
  };
  ws.onclose = () => {
    const conn = $("connState");
    conn.classList.remove("ok");
    conn.classList.add("bad");
    conn.innerHTML = '<span class="dot red"></span>连接中断';
    setTimeout(connect, retryDelay);
    retryDelay = Math.min(10000, retryDelay * 2);
  };
  ws.onerror = () => ws.close();
}

function observeChartResize(chart, element) {
  if (typeof ResizeObserver === "undefined") {
    return;
  }
  new ResizeObserver(() => {
    try {
      chart.resize();
    } catch (error) {
      // Chart may be disposed during teardown.
    }
  }).observe(element);
}

window.addEventListener("resize", () => {
  if (trendChart) trendChart.resize();
  if (topoChart) topoChart.resize();
});

setInterval(() => {
  $("clock").textContent = fmtClock();
  $("statUptime").textContent = fmtUptime(state.startedAt);
}, 1000);

connect();
