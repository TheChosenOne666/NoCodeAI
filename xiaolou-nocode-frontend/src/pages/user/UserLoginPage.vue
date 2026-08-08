<template>
  <div id="userLoginPage">
    <!-- 左上角品牌标识 -->
    <RouterLink to="/" class="brand-logo">
      <img class="brand-logo-img" src="@/assets/logo.png" alt="Logo" />
      <span class="brand-logo-text">No Code零代码</span>
    </RouterLink>
    <!-- 全屏背景图 -->
    <img class="login-bg" :src="loginBg" alt="" />
    <!-- 鼠标跟随 + 斜向流光层 -->
    <div class="userLoginAurora"></div>
    <!-- 左侧品牌文案 -->
    <div class="hero-text">
      <h1 class="hero-title">
        <span class="tagline">不会写代码却想开发应用？<br />别让代码成为创意的阻碍！</span>
      </h1>
      <p class="hero-sub">NoCode 用一句话把你的创意变成可落地的 Web 应用</p>
      <ul class="hero-feats">
        <li>
          <span class="feat-dot"></span>
          <div>
            <strong>自然语言编程</strong>
            <span>使用自然语言描述想法，NoCode 自动解读并转化为完整功能，无需编程经验即可生成可用能力。</span>
          </div>
        </li>
        <li>
          <span class="feat-dot"></span>
          <div>
            <strong>局部定位修改</strong>
            <span>使用 Edit 功能，可针对定位内容进行局部修改及完善。</span>
          </div>
        </li>
        <li>
          <span class="feat-dot"></span>
          <div>
            <strong>实时预览效果</strong>
            <span>根据对话内容即时渲染、呈现页面，可实时查看每次对话后的实际效果。</span>
          </div>
        </li>
        <li>
          <span class="feat-dot"></span>
          <div>
            <strong>一键部署分享</strong>
            <span>应用完成后，代码将自动上传到仓库，可直接分享链接给他人使用，简化发布流程。</span>
          </div>
        </li>
      </ul>
    </div>
    <!-- 右侧：登录表单 -->
    <div class="auth-card">
      <h2 class="title">欢迎回来</h2>
      <div class="desc">登录 No Code，让创意瞬间转化为精美网站</div>
      <a-form :model="formState" name="basic" autocomplete="off" @finish="handleSubmit">
        <a-form-item name="userAccount" :rules="[{ required: true, message: '请输入账号' }]">
          <a-input v-model:value="formState.userAccount" placeholder="请输入账号" size="large" autocomplete="off" />
        </a-form-item>
        <a-form-item
          name="userPassword"
          :rules="[
            { required: true, message: '请输入密码' },
            { min: 8, message: '密码长度不能小于 8 位' },
          ]"
        >
          <a-input-password v-model:value="formState.userPassword" placeholder="请输入密码" size="large" autocomplete="new-password" />
        </a-form-item>
        <div class="tips">
          没有账号
          <RouterLink to="/user/register">去注册</RouterLink>
        </div>
        <a-form-item>
          <a-button type="primary" html-type="submit" class="submit-btn">登录</a-button>
        </a-form-item>
      </a-form>
    </div>
    <!-- 底部版权 -->
    <div class="auth-footer">©2026 No Code-小楼创作</div>
  </div>
</template>
<script lang="ts" setup>
import { reactive, onMounted, onUnmounted } from 'vue'
import { userLogin } from '@/api/userController.ts'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import loginBg from '@/assets/login-bg.png'

const formState = reactive<API.UserLoginRequest>({
  userAccount: '',
  userPassword: '',
})

const router = useRouter()
const loginUserStore = useLoginUserStore()

// 鼠标跟随光效处理函数
let handleMouseMove: ((e: MouseEvent) => void) | null = null

/**
 * 提交表单
 * @param values
 */
const handleSubmit = async (values: any) => {
  const res = await userLogin(values)
  // 登录成功，把登录态保存到全局状态中
  if (res.data.code === 0 && res.data.data) {
    await loginUserStore.fetchLoginUser()
    message.success('登录成功')
    router.push({
      path: '/',
      replace: true,
    })
  } else {
    message.error('登录失败，' + res.data.message)
  }
}

onMounted(() => {
  // 鼠标跟随光效，与首页保持一致
  handleMouseMove = (e: MouseEvent) => {
    const { clientX, clientY } = e
    const { innerWidth, innerHeight } = window
    const x = (clientX / innerWidth) * 100
    const y = (clientY / innerHeight) * 100
    document.documentElement.style.setProperty('--mouse-x', `${x}%`)
    document.documentElement.style.setProperty('--mouse-y', `${y}%`)
  }
  document.addEventListener('mousemove', handleMouseMove)
})

onUnmounted(() => {
  if (handleMouseMove) {
    document.removeEventListener('mousemove', handleMouseMove)
  }
})
</script>

<style scoped>
#userLoginPage {
  width: 100%;
  min-height: 100vh;
  margin: 0;
  padding: 0;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  position: relative;
  overflow: hidden;
}

/* 全屏背景图：淡纹理 + 积木人物，铺在最底层 */
.login-bg {
  position: absolute;
  inset: 0;
  z-index: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center;
  opacity: 1;
  transform-origin: center;
  animation: bgBreathe 24s ease-in-out infinite alternate;
}

/* 背景图极缓慢呼吸式缩放平移，制造流动感（不改变构图与人物） */
@keyframes bgBreathe {
  0% { transform: scale(1) translate(0, 0); }
  100% { transform: scale(1.06) translate(-1%, -1%); }
}

/* 与首页一致的轻量光晕/网格叠加，放在背景图之上但不遮挡人物 */
#userLoginPage::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 1;
  background:
    radial-gradient(circle at 20% 80%, rgba(59, 130, 246, 0.15) 0%, transparent 50%),
    radial-gradient(circle at 80% 20%, rgba(139, 92, 246, 0.12) 0%, transparent 50%),
    radial-gradient(circle at 40% 40%, rgba(16, 185, 129, 0.08) 0%, transparent 50%),
    linear-gradient(rgba(59, 130, 246, 0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(59, 130, 246, 0.05) 1px, transparent 1px),
    linear-gradient(rgba(139, 92, 246, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(139, 92, 246, 0.04) 1px, transparent 1px);
  background-size:
    auto,
    auto,
    auto,
    100px 100px,
    100px 100px,
    20px 20px,
    20px 20px;
  pointer-events: none;
  animation: gridFloat 20s ease-in-out infinite;
}

/* 极光流光层：一条缓慢旋转的彩色光带扫过，低透明度不抢人物，增加高级流动感 */
#userLoginPage::after {
  content: '';
  position: absolute;
  inset: -40%;
  z-index: 1;
  background:
    conic-gradient(
      from 0deg at 50% 50%,
      transparent 0deg,
      rgba(59, 130, 246, 0.10) 60deg,
      transparent 120deg,
      rgba(139, 92, 246, 0.10) 200deg,
      transparent 260deg,
      rgba(16, 185, 129, 0.08) 320deg,
      transparent 360deg
    );
  pointer-events: none;
  mix-blend-mode: screen;
  animation: auroraSpin 28s linear infinite;
}

@keyframes auroraSpin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

/* 动态光效（鼠标跟随 + 斜向流光）叠加在极光层之上 */
.userLoginAurora {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 1;
  background:
    radial-gradient(
      600px circle at var(--mouse-x, 50%) var(--mouse-y, 50%),
      rgba(59, 130, 246, 0.08) 0%,
      rgba(139, 92, 246, 0.06) 40%,
      transparent 80%
    ),
    linear-gradient(45deg, transparent 30%, rgba(59, 130, 246, 0.04) 50%, transparent 70%),
    linear-gradient(-45deg, transparent 30%, rgba(139, 92, 246, 0.04) 50%, transparent 70%);
  pointer-events: none;
  animation: lightPulse 8s ease-in-out infinite alternate;
}

/* 左侧品牌文案 */
.hero-text {
  position: absolute;
  top: 50%;
  left: 8vw;
  transform: translateY(-50%);
  z-index: 2;
  max-width: 520px;
  animation: heroFadeIn 1s ease-out;
}

.hero-title {
  margin: 0 0 14px;
  font-size: 32px;
  font-weight: 800;
  line-height: 1.35;
  color: #0f172a;
}

/* 标题流动渐变高光（内容不变，仅增加高级感） */
.hero-title .tagline {
  background: linear-gradient(100deg, #0f172a 0%, #0f172a 35%, #3b82f6 50%, #8b5cf6 65%, #0f172a 80%, #0f172a 100%);
  background-size: 250% auto;
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  animation: textFlow 7s linear infinite;
}

@keyframes textFlow {
  to { background-position: 250% center; }
}

.hero-title .accent {
  color: #3b82f6;
}

.hero-sub {
  margin: 0 0 28px;
  font-size: 17px;
  font-weight: 600;
  color: #3b82f6;
  line-height: 1.5;
  background: linear-gradient(100deg, #3b82f6 0%, #8b5cf6 50%, #10b981 100%);
  background-size: 200% auto;
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  animation: textFlow 8s linear infinite;
}

.hero-feats {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 特性列表错峰滑入 */
.hero-feats li {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  opacity: 0;
  transform: translateX(-16px);
  animation: featIn 0.7s ease-out forwards;
}

.hero-feats li:nth-child(1) { animation-delay: 0.4s; }
.hero-feats li:nth-child(2) { animation-delay: 0.55s; }
.hero-feats li:nth-child(3) { animation-delay: 0.7s; }
.hero-feats li:nth-child(4) { animation-delay: 0.85s; }

@keyframes featIn {
  to { opacity: 1; transform: translateX(0); }
}

.feat-dot {
  flex-shrink: 0;
  width: 8px;
  height: 8px;
  margin-top: 6px;
  border-radius: 50%;
  background: #3b82f6;
  box-shadow: 0 0 0 0 rgba(59, 130, 246, 0.4);
  animation: dotPulse 2.4s ease-in-out infinite;
}

@keyframes dotPulse {
  0% { box-shadow: 0 0 0 0 rgba(59, 130, 246, 0.4); }
  70% { box-shadow: 0 0 0 8px rgba(59, 130, 246, 0); }
  100% { box-shadow: 0 0 0 0 rgba(59, 130, 246, 0); }
}

.hero-feats li div {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.hero-feats strong {
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}

.hero-feats li span {
  font-size: 13px;
  line-height: 1.6;
  color: #475569;
}

@keyframes heroFadeIn {
  0% { opacity: 0; transform: translateY(-45%); }
  100% { opacity: 1; transform: translateY(-50%); }
}

/* 毛玻璃卡片：固定在右侧 */
.auth-card {
  position: relative;
  z-index: 2;
  width: 100%;
  max-width: 440px;
  flex-shrink: 0;
  margin-right: 8vw;
  padding: 48px 40px;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.6);
  border-radius: 24px;
  box-shadow: 0 20px 60px rgba(30, 41, 59, 0.15);
}

/* 卡片流动光描边 */
.auth-card::before {
  content: '';
  position: absolute;
  inset: -1px;
  border-radius: 25px;
  padding: 1px;
  background: linear-gradient(
    130deg,
    rgba(59, 130, 246, 0.6),
    rgba(139, 92, 246, 0.1),
    rgba(16, 185, 129, 0.5),
    rgba(59, 130, 246, 0.6)
  );
  background-size: 300% 300%;
  -webkit-mask:
    linear-gradient(#fff 0 0) content-box,
    linear-gradient(#fff 0 0);
  -webkit-mask-composite: xor;
  mask-composite: exclude;
  pointer-events: none;
  animation: borderFlow 6s linear infinite;
}

@keyframes borderFlow {
  0% { background-position: 0% 50%; }
  100% { background-position: 300% 50%; }
}

.title {
  text-align: center;
  margin: 0 0 12px;
  font-size: 32px;
  font-weight: 700;
  line-height: 1.2;
  background: linear-gradient(135deg, #3b82f6 0%, #8b5cf6 50%, #10b981 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  animation: titleShimmer 3s ease-in-out infinite;
}

@keyframes titleShimmer {
  0%, 100% { background-position: 0% 50%; }
  50% { background-position: 100% 50%; }
}

.desc {
  text-align: center;
  color: #64748b;
  font-size: 14px;
  margin-bottom: 32px;
}

/* 输入框美化 */
:deep(.ant-input-affix-wrapper),
:deep(.ant-input) {
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(59, 130, 246, 0.15);
  transition: all 0.3s;
}

:deep(.ant-input-affix-wrapper:hover),
:deep(.ant-input:hover) {
  border-color: rgba(59, 130, 246, 0.4);
}

:deep(.ant-input-affix-wrapper-focused),
:deep(.ant-input:focus) {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.12);
  background: rgba(255, 255, 255, 1);
}

.tips {
  text-align: right;
  color: #94a3b8;
  font-size: 13px;
  margin-bottom: 16px;
}

.tips a {
  color: #3b82f6;
  margin-left: 4px;
  transition: color 0.2s;
}

.tips a:hover {
  color: #2563eb;
}

/* 提交按钮 */
.submit-btn {
  width: 100%;
  height: 46px;
  border-radius: 12px;
  font-size: 16px;
  font-weight: 600;
  background: linear-gradient(135deg, #3b82f6 0%, #8b5cf6 100%);
  border: none;
  box-shadow: 0 8px 24px rgba(59, 130, 246, 0.3);
  transition: all 0.3s;
}

.submit-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 32px rgba(59, 130, 246, 0.4);
}

.auth-footer {
  position: absolute;
  bottom: 20px;
  left: 0;
  right: 0;
  text-align: center;
  font-size: 13px;
  color: rgba(100, 116, 139, 0.8);
  letter-spacing: 0.5px;
}

.brand-logo {
  position: absolute;
  top: 24px;
  left: 32px;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
}

.brand-logo-img {
  width: 36px;
  height: 36px;
  object-fit: contain;
  border-radius: 8px;
}

.brand-logo-text {
  font-size: 20px;
  font-weight: 700;
  color: #1e293b;
  letter-spacing: 0.5px;
}

@media (max-width: 900px) {
  #userLoginPage {
    justify-content: center;
  }
  .hero-text {
    display: none;
  }
  .auth-card {
    margin-right: 0;
  }
}

@media (max-width: 480px) {
  .auth-card {
    padding: 36px 24px;
    margin: 0 16px;
  }
  .title {
    font-size: 26px;
  }
}
</style>
