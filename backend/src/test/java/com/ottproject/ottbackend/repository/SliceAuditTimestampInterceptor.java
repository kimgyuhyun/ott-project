package com.ottproject.ottbackend.repository;

import java.time.LocalDateTime;
import org.hibernate.Interceptor;
import org.hibernate.type.Type;

/**
 * 슬라이스 테스트용 생성/수정 시각 채우기
 *
 * 슬라이스에는 Auditing 을 켜지 않는다(JpaSliceTestSupport 주석: 켜면 JVM 전역 애스펙트가 다른 슬라이스로 샌다).
 * 픽스처가 만드는 엔티티는 시각을 직접 채우면 되지만, 검증 대상 서비스가 스스로 만드는 엔티티
 * (정기결제 배치가 PG 호출 전에 선기록하는 PENDING 결제)는 테스트가 손댈 틈이 없다.
 * 그래서 Hibernate 세션 팩토리에 이 인터셉터를 물려 비어 있는 createdAt/updatedAt 만 채운다.
 *
 * 세션 팩토리 단위 설정이라 이 프로퍼티를 지정한 테스트 컨텍스트에만 적용된다:
 * spring.jpa.properties.hibernate.session_factory.interceptor=com.ottproject.ottbackend.repository.SliceAuditTimestampInterceptor
 */
public class SliceAuditTimestampInterceptor implements Interceptor {

    @Override
    public boolean onPersist(Object entity, Object id, Object[] state, String[] propertyNames, Type[] types) {
        boolean changed = false;
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < propertyNames.length; i++) {
            if (("createdAt".equals(propertyNames[i]) || "updatedAt".equals(propertyNames[i])) && state[i] == null) {
                state[i] = now;
                changed = true;
            }
        }
        return changed;
    }
}
