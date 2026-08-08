package com.diybracelet.mall.catalog.domain;

/**
 * 商品分类对应的物料类型。
 *
 * <p>珠子可进入小程序珠子目录、DIY 和 AI 推荐；绳类与其他饰品当前只作为
 * 独立库存商品维护，不能冒充珠子参与需要珠径的链路。</p>
 */
public enum ProductType {

    /** 可用于手串排列的珠子。 */
    BEAD,

    /** 弹力绳、编织绳等连接材料。 */
    CORD,

    /** 隔片、吊坠等非珠子饰品。 */
    ACCESSORY
}
