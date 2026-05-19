package com.ec.api.service;

import com.ec.api.dto.CartRequest;
import com.ec.api.dto.CartResponse;
import com.ec.api.entity.Cart;
import com.ec.api.entity.Product;
import com.ec.api.entity.User;
import com.ec.api.repository.CartRepository;
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
public class CartService {

    private final CartRepository cartRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    public CartService(CartRepository cartRepository,
                       UserRepository userRepository,
                       ProductRepository productRepository) {
        this.cartRepository = cartRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
    }

    public List<CartResponse> getCart(String username) {
        User user = findUser(username);
        return cartRepository.findByUserId(user.getUserId()).stream()
                .map(c -> {
                    Product p = productRepository.findById(c.getProductId()).orElse(null);
                    if (p == null) return null;
                    return new CartResponse(
                            c.getCartId(),
                            p.getProductId(),
                            p.getProductName(),
                            p.getPrice(),
                            c.getProductQty(),
                            p.getImage() != null ? p.getImage().getImageName() : null,
                            p.getStock() != null ? p.getStock().getStockQty() : 0
                    );
                })
                .filter(r -> r != null)
                .toList();
    }

    @Transactional
    public void addToCart(String username, CartRequest req) {
        User user = findUser(username);
        Product product = productRepository.findById(req.getProductId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "商品が見つかりません"));

        int stock = product.getStock() != null ? product.getStock().getStockQty() : 0;
        if (stock <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "在庫がありません");
        }

        Optional<Cart> existing = cartRepository.findByUserIdAndProductId(user.getUserId(), req.getProductId());
        if (existing.isPresent()) {
            Cart c = existing.get();
            c.setProductQty(c.getProductQty() + 1);
            c.setUpdateDate(LocalDate.now());
            cartRepository.save(c);
        } else {
            Cart c = new Cart();
            c.setUserId(user.getUserId());
            c.setProductId(req.getProductId());
            c.setProductQty(1);
            c.setCreateDate(LocalDate.now());
            c.setUpdateDate(LocalDate.now());
            cartRepository.save(c);
        }
    }

    @Transactional
    public void updateQty(String username, Long cartId, int qty) {
        Cart cart = findCartForUser(username, cartId);
        if (qty <= 0) {
            cartRepository.delete(cart);
        } else {
            cart.setProductQty(qty);
            cart.setUpdateDate(LocalDate.now());
            cartRepository.save(cart);
        }
    }

    @Transactional
    public void removeItem(String username, Long cartId) {
        Cart cart = findCartForUser(username, cartId);
        cartRepository.delete(cart);
    }

    private Cart findCartForUser(String username, Long cartId) {
        User user = findUser(username);
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "カートアイテムが見つかりません"));
        if (!cart.getUserId().equals(user.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "アクセス権限がありません");
        }
        return cart;
    }

    private User findUser(String username) {
        return userRepository.findByUserName(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ユーザーが見つかりません"));
    }
}
