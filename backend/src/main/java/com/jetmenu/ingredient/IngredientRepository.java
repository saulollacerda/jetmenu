package com.jetmenu.ingredient;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IngredientRepository extends JpaRepository<Ingredient, UUID> {

    boolean existsByNameAndMerchantId(String name, UUID merchantId);

    long countByMerchantId(UUID merchantId);

    /** Highest manual position currently used by the merchant, or {@code null} when it has none. */
    @Query("select max(i.position) from Ingredient i where i.merchant.id = :merchantId")
    Integer findMaxPositionByMerchantId(@Param("merchantId") UUID merchantId);

    /**
     * Shift-left the rows between the moved ingredient's old position (exclusive) and its new
     * position (inclusive) when it moves DOWN the list. Merchant-scoped, single bulk UPDATE.
     */
    @Modifying
    @Query("update Ingredient i set i.position = i.position - 1 "
            + "where i.merchant.id = :merchantId and i.position > :oldPosition and i.position <= :newPosition")
    void shiftRangeLeft(@Param("merchantId") UUID merchantId,
                        @Param("oldPosition") int oldPosition,
                        @Param("newPosition") int newPosition);

    /**
     * Shift-right the rows between the moved ingredient's new position (inclusive) and its old
     * position (exclusive) when it moves UP the list. Merchant-scoped, single bulk UPDATE.
     */
    @Modifying
    @Query("update Ingredient i set i.position = i.position + 1 "
            + "where i.merchant.id = :merchantId and i.position >= :newPosition and i.position < :oldPosition")
    void shiftRangeRight(@Param("merchantId") UUID merchantId,
                         @Param("oldPosition") int oldPosition,
                         @Param("newPosition") int newPosition);

    Optional<Ingredient> findByIdAndMerchantId(UUID id, UUID merchantId);

    List<Ingredient> findAllByMerchantId(UUID merchantId);

    Page<Ingredient> findAllByMerchantIdAndNameContainingIgnoreCase(UUID merchantId, String name, Pageable pageable);

    boolean existsByIdAndMerchantId(UUID id, UUID merchantId);

    void deleteByIdAndMerchantId(UUID id, UUID merchantId);

    boolean existsByCanonicalNameAndMerchantId(String canonicalName, UUID merchantId);

    boolean existsByCanonicalNameAndMerchantIdAndIdNot(String canonicalName, UUID merchantId, UUID id);

    /**
     * Variante resiliente do lookup canônico usada pelos imports de pedidos: se o
     * merchant tiver ingredientes duplicados no nome canônico (dados legados), retorna
     * um único registro determinístico em vez de estourar
     * {@code IncorrectResultSizeDataAccessException} e derrubar o import.
     */
    Optional<Ingredient> findFirstByCanonicalNameAndMerchantIdOrderByIdAsc(String canonicalName, UUID merchantId);

    /**
     * Retorna, dentre os nomes canônicos informados, os que já existem como ingrediente do
     * merchant. Usado para derivar quais subItems não-casados já foram cadastrados (e assim
     * esconder o botão de cadastro), com uma única consulta em vez de N lookups.
     */
    @Query("select i.canonicalName from Ingredient i "
            + "where i.merchant.id = :merchantId and i.canonicalName in :names")
    List<String> findExistingCanonicalNames(@Param("merchantId") UUID merchantId,
                                            @Param("names") Collection<String> names);
}
