package cn.edu.campus.food.service;

/** Future authentication must supply the identity here, never from an untrusted request userId. */
public interface CurrentUser {
    long id();
}
