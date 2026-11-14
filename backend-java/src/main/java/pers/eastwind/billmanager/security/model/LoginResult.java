package pers.eastwind.billmanager.security.model;

public record LoginResult(String token, AuthorityUser account) {
}
