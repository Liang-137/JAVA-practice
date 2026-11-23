package cn.edu.campus.food.service;

import org.springframework.stereotype.Component;

@Component
public class LocalCurrentUser implements CurrentUser {
    @Override public long id() { return 1L; }
}
