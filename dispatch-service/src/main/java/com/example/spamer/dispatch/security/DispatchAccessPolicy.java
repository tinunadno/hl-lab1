package com.example.spamer.dispatch.security;

import com.example.spamer.common.error.BusinessException;
import com.example.spamer.common.error.ForbiddenException;
import com.example.spamer.common.security.CurrentUser;
import com.example.spamer.common.security.Roles;
import com.example.spamer.dispatch.dto.SpamSendRequest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Правила dispatch-service, зависящие от того, кто делает запрос. Какие роли вообще допускаются
 * к методу, решает {@code @PreAuthorize} на контроллере, а здесь — что именно им можно.
 */
@Component
public class DispatchAccessPolicy {

    private final int freeMaxMessages;

    public DispatchAccessPolicy(@Value("${spamer.limits.free-max-messages:10}") int freeMaxMessages) {
        this.freeMaxMessages = freeMaxMessages;
    }

    /**
     * Супервайзер отправляет от имени любого пользователя ({@code userId} обязателен). Клиент
     * отправляет только от своего имени и платит сам. У {@code USER_FREE} меньше лимит сообщений.
     *
     * @return запрос, в котором {@code userId} — тот, с чьего баланса спишутся деньги
     */
    public SpamSendRequest authorizeSend(SpamSendRequest req, CurrentUser actor) {
        if (actor.isSupervisor()) {
            if (req.userId() == null) {
                throw new BusinessException("userId is required when a supervisor sends on behalf of a user");
            }
            return req;
        }
        UUID self = actor.id();
        if (self == null || (req.userId() != null && !req.userId().equals(self))) {
            throw new ForbiddenException("You can only send from your own account");
        }
        if (actor.is(Roles.USER_FREE) && req.messageCount() > freeMaxMessages) {
            throw new ForbiddenException("USER_FREE can send at most " + freeMaxMessages
                    + " messages per request; subscribe to a service to lift the limit");
        }
        return new SpamSendRequest(
                self, req.serviceId(), req.victimContact(), req.messageBody(), req.messageCount());
    }

    /** Чей журнал виден: {@code null} — весь журнал (супервайзер), иначе только свои записи. */
    public UUID visibleOwner(CurrentUser actor) {
        if (actor.canSeeEveryone()) {
            return null;
        }
        if (actor.id() == null) {
            throw new ForbiddenException("Token does not belong to a user");
        }
        return actor.id();
    }

    public void checkCanRead(UUID ownerId, CurrentUser actor) {
        if (!actor.canSeeEveryone() && !actor.owns(ownerId)) {
            throw new ForbiddenException("You can only view your own spam log entries");
        }
    }
}
