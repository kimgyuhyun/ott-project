"use client";
import { useEffect, useState } from "react";
import { useAuth } from "@/lib/AuthContext";
import { getMembershipPlans, getUserMembership, getPaymentMethods, getPaymentHistory, type MembershipPlan, type UserMembership, type PaymentMethodResponse, type PaymentHistoryItem } from "@/lib/api/membership";

export function useMembershipData() {
  const { isAuthenticated, user, isInitialized } = useAuth();
  const [membershipPlans, setMembershipPlans] = useState<MembershipPlan[]>([]);
  const [userMembership, setUserMembership] = useState<UserMembership | null>(null);
  const [paymentMethods, setPaymentMethods] = useState<PaymentMethodResponse[]>([]);
  const [paymentHistory, setPaymentHistory] = useState<PaymentHistoryItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const load = async () => {
      setIsLoading(true);
      setError(null);
      try {
        const plans = await getMembershipPlans();
        setMembershipPlans(plans);

        if (isInitialized && isAuthenticated === true && user) {
          try {
            const [membership, methods, history] = await Promise.all([
              getUserMembership(),
              getPaymentMethods(),
              getPaymentHistory(),
            ]);
            setUserMembership(membership);
            setPaymentMethods(methods);
            setPaymentHistory(history);
          } catch (e) {
            // 401 등은 무시
          }
        }
      } catch (e) {
        setError('멤버십 플랜을 불러올 수 없습니다.');
      } finally {
        setIsLoading(false);
      }
    };
    if (!isInitialized) return; // 초기화 대기
    load();
  }, [isAuthenticated, user, isInitialized]);

  return {
    membershipPlans,
    userMembership,
    paymentMethods,
    paymentHistory,
    isLoading,
    error,
    reloadPaymentMethods: async () => {
      if (isAuthenticated !== true || !user) return;
      try {
        const methods = await getPaymentMethods();
        setPaymentMethods(methods);
      } catch {
        // ignore
      }
    },
    reloadUserMembership: async () => {
      if (isAuthenticated !== true || !user) return;
      try {
        const membership = await getUserMembership();
        setUserMembership(membership);
      } catch {
        // ignore
      }
    },
    reloadPaymentHistory: async () => {
      if (isAuthenticated !== true || !user) return;
      try {
        const history = await getPaymentHistory();
        setPaymentHistory(history);
      } catch {
        // ignore
      }
    },
  };
}


