import request from './request'
import type { ApiListParams, ApiPage } from '@/types'

export interface Product {
  id: number | string
  productCode: string
  productName: string
  spec?: string
  unit: string
  status: string
  remark?: string
  hasBom?: boolean
  createdTime?: string
  updatedTime?: string
}

export interface BomItem {
  id?: number | string
  materialId: number | string
  materialCode?: string
  materialName?: string
  qty: number
  unit: string
  lossRate?: number
  remark?: string
}

export interface ProductBom {
  id?: number | string
  productId?: number | string
  version?: string
  status?: string
  remark?: string
  items: BomItem[]
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

export function saveProductBom(id: number | string, payload: Partial<ProductBom>) {
  return request.put<ProductBom>(`/products/${id}/bom`, payload).then((res) => res.data)
}
