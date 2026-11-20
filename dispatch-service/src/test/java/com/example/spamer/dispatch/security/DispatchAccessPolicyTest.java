package com.example.spamer.dispatch.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.spamer.common.error.BusinessException;
import com.example.spamer.common.error.ForbiddenException;
import com.example.spamer.common.security.CurrentUser;
import com.example.spamer.dispatch.dto.SpamSendRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DispatchAccessPolicyTest {

    final DispatchAccessPolicy policy = new DispatchAccessPolicy(10);
    final UUID me = UUID.randomUUID();
    final UUID serviceId = UUID.randomUUID();

    private SpamSendRequest request(UUID userId, int count) {
        return new SpamSendRequest(userId, serviceId, "t@example.com", "hi", count);
    }

    @Test
    void supervisorSendsOnBehalfOfNamedUser() {
        CurrentUser boss = new CurrentUser(UUID.randomUUID(), "boss", "SUPERVISOR");
        SpamSendRequest req = request(me, 100);

        assertThat(policy.authorizeSend(req, boss)).isSameAs(req);
        assertThatThrownBy(() -> policy.authorizeSend(request(null, 1), boss))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void clientAlwaysPaysThemselves() {
        CurrentUser pro = new CurrentUser(me, "me", "USER_PRO");

        assertThat(policy.authorizeSend(request(null, 100), pro).userId()).isEqualTo(me);
        assertThat(policy.authorizeSend(request(me, 100), pro).userId()).isEqualTo(me);
        assertThatThrownBy(() -> policy.authorizeSend(request(UUID.randomUUID(), 1), pro))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void freeClientIsLimited() {
        CurrentUser free = new CurrentUser(me, "me", "USER_FREE");

        assertThat(policy.authorizeSend(request(null, 10), free).messageCount()).isEqualTo(10);
        assertThatThrownBy(() -> policy.authorizeSend(request(null, 11), free))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("10");
    }

    @Test
    void serviceTokenCannotSend() {
        CurrentUser service = new CurrentUser(null, "user-service", "SERVICE");

        assertThatThrownBy(() -> policy.authorizeSend(request(null, 1), service))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void logVisibilityDependsOnRole() {
        CurrentUser boss = new CurrentUser(UUID.randomUUID(), "boss", "SUPERVISOR");
        CurrentUser pro = new CurrentUser(me, "me", "USER_PRO");

        assertThat(policy.visibleOwner(boss)).isNull();
        assertThat(policy.visibleOwner(pro)).isEqualTo(me);
        assertThatThrownBy(() -> policy.visibleOwner(new CurrentUser(null, "x", "USER_PRO")))
                .isInstanceOf(ForbiddenException.class);

        policy.checkCanRead(UUID.randomUUID(), boss);
        policy.checkCanRead(me, pro);
        assertThatThrownBy(() -> policy.checkCanRead(UUID.randomUUID(), pro))
                .isInstanceOf(ForbiddenException.class);
    }
}
