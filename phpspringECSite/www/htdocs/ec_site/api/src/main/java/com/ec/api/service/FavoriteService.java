package com.ec.api.service;

import com.ec.api.dto.FavoriteResponse;
import com.ec.api.entity.Favorite;
import com.ec.api.entity.Product;
import com.ec.api.entity.User;
import com.ec.api.repository.FavoriteRepository;
import com.ec.api.repository.ProductRepository;
import com.ec.api.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    public FavoriteService(FavoriteRepository favoriteRepository,
                           UserRepository userRepository,
                           ProductRepository productRepository) {
        this.favoriteRepository = favoriteRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
    }

    public List<FavoriteResponse> getFavorites(String username) {
        User user = findUser(username);
        return favoriteRepository.findByUserId(user.getUserId()).stream()
                .map(f -> {
                    Product p = productRepository.findById(f.getProductId()).orElse(null);
                    if (p == null || p.getDeletedFlg() != 0) return null;
                    return FavoriteResponse.from(f.getFavoriteId(), p);
                })
                .filter(r -> r != null)
                .toList();
    }

    public List<Long> getFavoriteProductIds(String username) {
        User user = findUser(username);
        return favoriteRepository.findByUserId(user.getUserId()).stream()
                .map(Favorite::getProductId)
                .toList();
    }

    @Transactional
    public boolean toggle(String username, Long productId) {
        User user = findUser(username);
        productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "商品が見つかりません"));

        Optional<Favorite> existing = favoriteRepository.findByUserIdAndProductId(user.getUserId(), productId);
        if (existing.isPresent()) {
            favoriteRepository.delete(existing.get());
            return false;
        } else {
            Favorite f = new Favorite();
            f.setUserId(user.getUserId());
            f.setProductId(productId);
            f.setCreateDate(LocalDate.now());
            favoriteRepository.save(f);
            return true;
        }
    }

    private User findUser(String username) {
        return userRepository.findByUserName(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ユーザーが見つかりません"));
    }
}
