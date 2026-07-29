interface MoodCard {
  id: string
  name: string
  description: string
  imageUrl: string
  tone: string
}

interface ProductCard {
  id: string
  name: string
  likes: string
  imageUrl: string
}

const moods: MoodCard[] = [
  {
    id: 'gentle',
    name: '温柔',
    description: '温暖自己',
    imageUrl: '/assets/images/home/mood-gentle.png',
    tone: 'rose',
  },
  {
    id: 'luck',
    name: '好运',
    description: '好运自然来',
    imageUrl: '/assets/images/home/mood-luck.png',
    tone: 'lilac',
  },
  {
    id: 'energy',
    name: '元气',
    description: '光芒藏在心里',
    imageUrl: '/assets/images/home/mood-energy.png',
    tone: 'sun',
  },
  {
    id: 'relax',
    name: '松弛',
    description: '慢下来，呼吸轻盈',
    imageUrl: '/assets/images/home/mood-relax.png',
    tone: 'sky',
  },
]

const products: ProductCard[] = [
  {
    id: 'gentle-guardian',
    name: '温柔守护',
    likes: '1.2w',
    imageUrl: '/assets/images/home/product-gentle-guardian.png',
  },
  {
    id: 'lucky-blessing',
    name: '幸运眷顾',
    likes: '9980',
    imageUrl: '/assets/images/home/product-lucky-blessing.png',
  },
  {
    id: 'confidence-light',
    name: '自信之光',
    likes: '8563',
    imageUrl: '/assets/images/home/product-confidence-light.png',
  },
  {
    id: 'cloud-breath',
    name: '云端之息',
    likes: '7321',
    imageUrl: '/assets/images/home/product-cloud-breath.png',
  },
]

Page({
  data: {
    moods,
    products,
    selectedMoodId: moods[0].id,
    scheme: {
      name: '温柔是我的超能力',
      crystals: '柔粉晶 · 紫水晶 · 白水晶',
      createdAt: '2024-06-01',
      imageUrl: '/assets/images/home/product-gentle-guardian.png',
    },
  },

  navigate(url: string) {
    wx.navigateTo({
      url,
      fail: () => {
        wx.showToast({
          title: '页面暂时无法打开',
          icon: 'none',
        })
      },
    })
  },

  openDiy() {
    this.navigate('/pages/diy/editor')
  },

  openRecommendation() {
    this.navigate('/pages/recommendation/index')
  },

  openCatalog() {
    this.navigate('/pages/catalog/index')
  },

  openSchemes() {
    this.navigate('/pages/diy/schemes')
  },

  selectMood(event: WechatMiniprogram.TouchEvent) {
    const moodId = String(event.currentTarget.dataset.moodId || '')
    if (!moodId) return
    this.setData({ selectedMoodId: moodId })
  },

  rotateMoods() {
    const currentMoods = this.data.moods as MoodCard[]
    if (currentMoods.length < 2) return
    const rotatedMoods = [...currentMoods.slice(1), currentMoods[0]]
    this.setData({
      moods: rotatedMoods,
      selectedMoodId: rotatedMoods[0].id,
    })
  },

  backToTop() {
    const scrollPage = wx as typeof wx & {
      pageScrollTo(options: { scrollTop: number; duration: number }): void
    }
    scrollPage.pageScrollTo({
      scrollTop: 0,
      duration: 300,
    })
  },

  onShareAppMessage() {
    return {
      title: '一颗珠子，一种心情｜来设计你的能量手串',
      path: '/pages/home/index',
      imageUrl: '/assets/images/home/hero-bracelet-lotus.png',
    }
  },
})
