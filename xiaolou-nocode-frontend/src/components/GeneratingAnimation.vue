<template>
  <div class="generating-animation">
    <div class="cat-container">
      <svg class="cat-svg" viewBox="0 0 120 120" xmlns="http://www.w3.org/2000/svg">
        <!-- 耳朵 -->
        <path class="cat-ear left" d="M35 45 L25 15 L55 35 Z" />
        <path class="cat-ear right" d="M85 45 L95 15 L65 35 Z" />
        <!-- 头 -->
        <ellipse class="cat-head" cx="60" cy="65" rx="38" ry="34" />
        <!-- 面罩 -->
        <path class="cat-mask" d="M28 58 Q60 50 92 58 Q95 72 85 78 Q60 85 35 78 Q25 72 28 58 Z" />
        <!-- 眼睛 -->
        <ellipse class="cat-eye" cx="46" cy="64" rx="5" ry="6" />
        <ellipse class="cat-eye" cx="74" cy="64" rx="5" ry="6" />
        <!-- 瞳孔 -->
        <circle class="cat-pupil" cx="46" cy="64" r="2" />
        <circle class="cat-pupil" cx="74" cy="64" r="2" />
        <!-- 鼻子 -->
        <circle class="cat-nose" cx="60" cy="74" r="2.5" />
        <!-- 嘴巴 -->
        <path class="cat-mouth" d="M55 80 Q60 84 65 80" fill="none" stroke-width="2" stroke-linecap="round" />
        <!-- 胡须 -->
        <line class="cat-whisker" x1="28" y1="72" x2="12" y2="68" stroke-width="1.5" stroke-linecap="round" />
        <line class="cat-whisker" x1="28" y1="76" x2="12" y2="78" stroke-width="1.5" stroke-linecap="round" />
        <line class="cat-whisker" x1="92" y1="72" x2="108" y2="68" stroke-width="1.5" stroke-linecap="round" />
        <line class="cat-whisker" x1="92" y1="76" x2="108" y2="78" stroke-width="1.5" stroke-linecap="round" />
        <!-- 星星装饰 -->
        <g class="sparkle sparkle-1">
          <path d="M20 25 L22 30 L27 30 L23 34 L25 39 L20 36 L15 39 L17 34 L13 30 L18 30 Z" />
        </g>
        <g class="sparkle sparkle-2">
          <path d="M100 20 L102 25 L107 25 L103 29 L105 34 L100 31 L95 34 L97 29 L93 25 L98 25 Z" />
        </g>
        <g class="sparkle sparkle-3">
          <path d="M105 90 L106 93 L109 93 L107 95 L108 98 L105 96 L102 98 L103 95 L101 93 L104 93 Z" />
        </g>
      </svg>
    </div>
    <p class="generating-text">生成中...</p>
  </div>
</template>

<script setup lang="ts">
// 纯展示组件，无逻辑
</script>

<style scoped>
.generating-animation {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #666;
}

.cat-container {
  width: 120px;
  height: 120px;
  animation: float 2.5s ease-in-out infinite;
}

.cat-svg {
  width: 100%;
  height: 100%;
}

.cat-head {
  fill: #1a1a1a;
}

.cat-ear {
  fill: #1a1a1a;
}

.cat-mask {
  fill: #2d2d2d;
}

.cat-eye {
  fill: #ffffff;
}

.cat-pupil {
  fill: #1a1a1a;
  animation: blink 3s infinite;
}

.cat-nose {
  fill: #ff8a8a;
}

.cat-mouth {
  stroke: #ffffff;
}

.cat-whisker {
  stroke: #4a4a4a;
}

.sparkle {
  fill: #ffd43b;
  animation: sparkle 1.8s ease-in-out infinite;
}

.sparkle-1 {
  animation-delay: 0s;
}

.sparkle-2 {
  animation-delay: 0.6s;
}

.sparkle-3 {
  animation-delay: 1.2s;
}

.generating-text {
  margin-top: 16px;
  font-size: 16px;
  color: #666;
  letter-spacing: 2px;
  animation: pulse 1.8s ease-in-out infinite;
}

@keyframes float {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-8px);
  }
}

@keyframes blink {
  0%, 96%, 100% {
    transform: scaleY(1);
  }
  98% {
    transform: scaleY(0.1);
  }
}

@keyframes sparkle {
  0%, 100% {
    opacity: 0.3;
    transform: scale(0.8) rotate(0deg);
  }
  50% {
    opacity: 1;
    transform: scale(1.1) rotate(15deg);
  }
}

@keyframes pulse {
  0%, 100% {
    opacity: 0.7;
  }
  50% {
    opacity: 1;
  }
}
</style>
