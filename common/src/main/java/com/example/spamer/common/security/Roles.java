package com.example.spamer.common.security;

/**
 * Имена ролей, которые попадают в claim {@code role} JWT-токена.
 * Spring Security видит их как authority {@code ROLE_<имя>}.
 */
public final class Roles {

    /** Создаёт пользователей, видит и меняет всё. */
    public static final String SUPERVISOR = "SUPERVISOR";
    /** Оператор закупок: ведёт каталог сервисов и пул прокси, рассылки не делает. */
    public static final String BUY_OPERATOR = "BUY_OPERATOR";
    /** Клиент с подпиской: рассылки до лимита запроса. */
    public static final String USER_PRO = "USER_PRO";
    /** Бесплатный клиент: рассылки с пониженным лимитом сообщений. */
    public static final String USER_FREE = "USER_FREE";
    /** Получатель: видит только свой профиль. */
    public static final String VICTIM = "VICTIM";
    /** Не роль пользователя: так подписаны вызовы между сервисами. */
    public static final String SERVICE = "SERVICE";

    private Roles() {
    }
}
