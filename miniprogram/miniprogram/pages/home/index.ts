Page({
  data: {
    orbitBeads: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10],
    features: [
      { value: '40+', label: '精选材质' },
      { value: '自由', label: '搭配顺序' },
      { value: '可存', label: '设计方案' },
    ],
  },

  openCatalog() {
    wx.navigateTo({
      url: '/pages/catalog/index',
    })
  },

  openRecommendation() {
    wx.navigateTo({
      url: '/pages/recommendation/index',
    })
  },
})
