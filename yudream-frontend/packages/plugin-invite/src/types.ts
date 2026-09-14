/** 用户端本人绑定记录（不含其他用户信息） */
export interface MyBinding {
  id: string
  code: string
  remark: string
  boundAt: number
}

/** 管理端绑定记录（含绑定人） */
export interface AdminBinding extends MyBinding {
  userId: string
  username: string
}

export interface BindingPage<T extends MyBinding = MyBinding> {
  records: T[]
  total: number
}
