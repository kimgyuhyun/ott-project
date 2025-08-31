declare global {
  interface Window {
    IMP: {
      init: (impCode: string) => void;
      request_pay: (
        data: IamportRequestPayData,
        callback: (response: IamportResponse) => void
      ) => void;
    };
  }
}

export interface IamportRequestPayData {
  pg: string;
  pay_method: string;
  merchant_uid: string;
  amount: number;
  name: string;
  buyer_email?: string;
  buyer_name?: string;
  buyer_tel?: string;
  app_scheme?: string;
  popup?: boolean;
  digital?: boolean;
  m_redirect_url?: string;
  notice_url?: string;
  escrow?: boolean;
  customer_uid?: string;
  tax_free?: number;
  language?: string;
  currency?: string;
  vbank_due?: string;
  biz_num?: string;
  custom_data?: any;
  period?: {
    from: string;
    to: string;
  };
  use_deposit?: boolean;
  use_card?: boolean;
  use_vbank?: boolean;
  use_trans?: boolean;
  use_phone?: boolean;
  use_culturevoucher?: boolean;
  use_giftculture?: boolean;
  use_phonebill?: boolean;
  use_naverpay?: boolean;
  use_kakaopay?: boolean;
  use_payco?: boolean;
  use_toss?: boolean;
  use_lpay?: boolean;
  use_ssgpay?: boolean;
  use_applepay?: boolean;
  use_samsungpay?: boolean;
  use_chai?: boolean;
  use_paypal?: boolean;
  use_tosspay?: boolean;
  use_mobilians?: boolean;
  use_card_direct?: boolean;
  use_vbank_direct?: boolean;
  use_trans_direct?: boolean;
  use_phone_direct?: boolean;
  use_culturevoucher_direct?: boolean;
  use_giftculture_direct?: boolean;
  use_phonebill_direct?: boolean;
  use_naverpay_direct?: boolean;
  use_kakaopay_direct?: boolean;
  use_payco_direct?: boolean;
  use_toss_direct?: boolean;
  use_lpay_direct?: boolean;
  use_ssgpay_direct?: boolean;
  use_applepay_direct?: boolean;
  use_samsungpay_direct?: boolean;
  use_chai_direct?: boolean;
  use_paypal_direct?: boolean;
  use_tosspay_direct?: boolean;
  use_mobilians_direct?: boolean;
}

export interface IamportResponse {
  success: boolean;
  error_msg?: string;
  imp_uid?: string;
  merchant_uid?: string;
  pay_method?: string;
  paid_amount?: number;
  status?: string;
  name?: string;
  pg_provider?: string;
  pg_tid?: string;
  buyer_name?: string;
  buyer_email?: string;
  buyer_tel?: string;
  buyer_addr?: string;
  buyer_postcode?: string;
  custom_data?: any;
  paid_at?: number;
  receipt_url?: string;
  card_name?: string;
  bank_name?: string;
  card_quota?: number;
  card_number?: string;
  vbank_name?: string;
  vbank_num?: string;
  vbank_holder?: string;
  vbank_date?: number;
}

export {};
