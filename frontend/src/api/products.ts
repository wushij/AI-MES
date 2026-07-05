import request from './request'
import type { ApiListParams, ApiPage } from '@/types'

export interface Product {
  id: number | string
  productCode: string
  productName: string
  spec?: string
  unit: string
  stockQty?: number
  status: string
  remark?: string
  hasBom?: boolean
  createdTime?: string
  updatedTime?: string
}

export interface ProductTransaction {
  id: number | string
  productId?: number | string
  txnType: string
  qty: number
  beforeQty: number
  afterQty: number
  refType?: string
  refId?: number | string
  operatorId?: number | string
  remark?: string
  createdTime?: string
}

export interface BomItem {
  id?: number | string
  materialId: number | string
  materialCode?: string
  materialName?: string
  qty: number
  unit: string
  lossRate?: number
  materialType?: string
  operationNames?: string[]
  operationNamesLabel?: string
  remark?: string
}

export interface BomDetailItem {
  operationName?: string
  operationCode?: string
  seqNo?: number
  materialId?: number | string
  materialCode?: string
  materialName?: string
  qty?: number
  unit?: string
  materialType?: string
}

export interface ProductBom {
  id?: number | string
  productId?: number | string
  routingId?: number | string
  routeName?: string
  routeCode?: string
  version?: string
  source?: string
  editable?: boolean
  status?: string
  remark?: string
  items: BomItem[]
  details?: BomDetailItem[]
}

export interface ProductDetail extends Product {
  bom?: ProductBom
}

export function getProducts(params?: ApiListParams) {
  return request.get<ApiPage<Product>>('/products', { params }).then((res) => res.data)
}

export function getProductOptions() {
  return request.get<Array<{ id: number | string; productCode: string; productName: string; unit: string }>>('/products/options').then((res) => res.data)
}

export function getProduct(id: number | string) {
  return request.get<ProductDetail>(`/products/${id}`).then((res) => res.data)
}

export function createProduct(payload: Partial<Product>) {
  return request.post<Product>('/products', payload).then((res) => res.data)
}

export function updateProduct(id: number | string, payload: Partial<Product>) {
  return request.put<Product>(`/products/${id}`, payload).then((res) => res.data)
}

export function deleteProduct(id: number | string) {
  return request.delete(`/products/${id}`).then((res) => res.data)
}

export function getProductBom(id: number | string) {
  return request.get<ProductBom>(`/products/${id}/bom`).then((res) => res.data)
}

export function getProductTransactions(id: number | string) {
  return request.get<ProductTransaction[]>(`/products/${id}/transactions`).then((res) => res.data)
}

export function saveProductBom(id: number | string, payload: Partial<ProductBom>) {
  return request.put<ProductBom>(`/products/${id}/bom`, payload).then((res) => res.data)
}
