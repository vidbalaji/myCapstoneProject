package org.example.cartservice_aug2026.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.cartservice_aug2026.clients.KafkaProducerClient;
import org.example.cartservice_aug2026.clients.ProductServiceClient;
import org.example.cartservice_aug2026.dtos.ProductDto;
import org.example.cartservice_aug2026.exceptions.CartItemNotFoundException;
import org.example.cartservice_aug2026.exceptions.ProductNotFoundException;
import org.example.cartservice_aug2026.models.Cart;
import org.example.cartservice_aug2026.repos.CartRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepo cartRepo;

    @Mock
    private ProductServiceClient productServiceClient;

    @Mock
    private KafkaProducerClient kafkaProducerClient;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private CartService cartService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(cartService, "maxQuantityPerItem", 10);
        lenient().when(cartRepo.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private ProductDto product(long id, double price) {
        ProductDto productDto = new ProductDto();
        productDto.setId(id);
        productDto.setName("Product " + id);
        productDto.setPrice(price);
        return productDto;
    }

    @Test
    void addItem_ToEmptyCart_CreatesCartAndPublishesEvent() {
        when(cartRepo.findById(7L)).thenReturn(Optional.empty());
        when(productServiceClient.getProduct(1L)).thenReturn(product(1L, 499.0));

        Cart cart = cartService.addItem(7L, 1L, 2);

        assertEquals(7L, cart.getUserId());
        assertEquals(1, cart.getItems().size());
        assertEquals(2, cart.getItems().get(0).getQuantity());
        assertEquals(499.0, cart.getItems().get(0).getUnitPrice());
        verify(kafkaProducerClient).sendMessage(eq(CartService.CART_EVENTS_TOPIC), eq("7"), contains("ITEM_ADDED"));
    }

    @Test
    void addItem_SameProductTwice_MergesQuantity() {
        Cart existing = new Cart();
        existing.setUserId(7L);
        when(cartRepo.findById(7L)).thenReturn(Optional.of(existing));
        when(productServiceClient.getProduct(1L)).thenReturn(product(1L, 100.0));

        cartService.addItem(7L, 1L, 2);
        Cart cart = cartService.addItem(7L, 1L, 3);

        assertEquals(1, cart.getItems().size());
        assertEquals(5, cart.getItems().get(0).getQuantity());
    }

    @Test
    void addItem_BeyondMaxQuantity_IsRejected() {
        assertThrows(IllegalArgumentException.class, () -> cartService.addItem(7L, 1L, 11));
        assertThrows(IllegalArgumentException.class, () -> cartService.addItem(7L, 1L, 0));
        verifyNoInteractions(productServiceClient);
    }

    @Test
    void addItem_UnknownProduct_PropagatesNotFound() {
        when(productServiceClient.getProduct(99L)).thenThrow(new ProductNotFoundException("nope"));
        assertThrows(ProductNotFoundException.class, () -> cartService.addItem(7L, 99L, 1));
        verify(cartRepo, never()).save(any());
    }

    @Test
    void removeItem_NotInCart_Throws() {
        when(cartRepo.findById(7L)).thenReturn(Optional.empty());
        assertThrows(CartItemNotFoundException.class, () -> cartService.removeItem(7L, 1L));
    }

    @Test
    void kafkaFailure_DoesNotFailTheCartUpdate() {
        when(cartRepo.findById(7L)).thenReturn(Optional.empty());
        when(productServiceClient.getProduct(1L)).thenReturn(product(1L, 10.0));
        doThrow(new RuntimeException("broker down")).when(kafkaProducerClient).sendMessage(anyString(), anyString(), anyString());

        Cart cart = cartService.addItem(7L, 1L, 1);
        assertEquals(1, cart.getItems().size());
    }
}
