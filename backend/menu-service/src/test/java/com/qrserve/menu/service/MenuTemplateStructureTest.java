package com.qrserve.menu.service;

import com.qrserve.menu.dto.CreateMenuTemplateRequest;
import com.qrserve.menu.dto.UpdateMenuTemplateRequest;
import com.qrserve.menu.entity.MenuTemplateEntity;
import com.qrserve.menu.entity.MenuTemplateEntity.FontFamily;
import com.qrserve.menu.entity.MenuTemplateEntity.HeaderAlignment;
import com.qrserve.menu.entity.MenuTemplateEntity.ImageAspectRatio;
import com.qrserve.menu.entity.MenuTemplateEntity.ImagePosition;
import com.qrserve.menu.entity.MenuTemplateEntity.ItemCardStyle;
import com.qrserve.menu.entity.MenuTemplateEntity.LayoutStructure;
import com.qrserve.menu.repository.MenuRepository;
import com.qrserve.menu.repository.MenuTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The layout/imagery/typography half of a template definition.
 *
 * <p>A "legacy row" below means a template written before
 * db/manual/001-menu-template-structure.sql ran, whose structural columns are
 * still NULL.
 *
 * <p>HOW A NULL ACTUALLY REACHES A FIELD, because it is not the obvious way:
 * Lombok has restored {@code @Builder.Default} values in the generated
 * {@code @NoArgsConstructor} since 1.18.2 (this repo is on 1.18.46), so
 * {@code new MenuTemplateEntity()} yields a fully defaulted object, NOT nulls.
 * The null comes one step later - Hibernate instantiates through that no-arg
 * constructor and then populates each field from the ResultSet, so a NULL
 * column overwrites the default that was just set. {@code legacyRow()} below
 * reproduces that by nulling the fields explicitly after construction, which
 * is what the persistence layer does.
 *
 * <p>This distinction is the whole justification for {@code normalised()}: if
 * the defaults survived a load, it would be dead code.
 */
class MenuTemplateStructureTest {

    private MenuTemplateRepository repository;
    private MenuRepository menuRepository;
    private MenuTemplateService service;

    @BeforeEach
    void setUp() {
        repository = mock(MenuTemplateRepository.class);
        menuRepository = mock(MenuRepository.class);
        service = new MenuTemplateService(repository, menuRepository);
    }

    /**
     * A row as Hibernate hands it back before the structure migration: built
     * through the no-arg constructor, then field-populated from a ResultSet
     * whose structural columns are all NULL.
     */
    private static MenuTemplateEntity legacyRow(String key) {
        MenuTemplateEntity row = new MenuTemplateEntity();
        row.setKey(key);
        row.setDisplayName(key);
        row.setBackgroundMode(MenuTemplateEntity.BackgroundMode.LIGHT);
        row.setAccentToken(MenuTemplateEntity.AccentToken.BRAND);
        // The NULL columns. Setting them explicitly is not test scaffolding —
        // it is precisely what the persistence layer does on load.
        row.setLayoutStructure(null);
        row.setItemCardStyle(null);
        row.setShowImages(null);
        row.setImagePosition(null);
        row.setImageAspectRatio(null);
        row.setFontFamily(null);
        row.setHeaderAlignment(null);
        row.setShowCoverImage(null);
        return row;
    }

    // ---- normalised() ----------------------------------------------------

    @Test
    void theNoArgConstructorKeepsBuilderDefaults() {
        // Documents the behaviour the rest of this file relies on. Lombok
        // 1.18.2+ restores @Builder.Default in @NoArgsConstructor, so a plain
        // `new` is fully defaulted — the nulls normalised() exists for come
        // from Hibernate overwriting those defaults with NULL columns, not
        // from construction. If this ever flips back, legacyRow()'s explicit
        // nulling becomes redundant rather than wrong.
        MenuTemplateEntity fresh = new MenuTemplateEntity();

        assertEquals(LayoutStructure.SINGLE_COLUMN, fresh.getLayoutStructure());
        assertEquals(ItemCardStyle.CARD_BORDERED, fresh.getItemCardStyle());
        assertEquals(Boolean.TRUE, fresh.getShowImages());
    }

    @Test
    void aLegacyRowReallyDoesCarryNullStructuralFields() {
        MenuTemplateEntity row = legacyRow("CLASSIC");

        // Guards the premise of every other test here: if a null could not
        // reach these fields, normalised() would be dead code.
        assertNull(row.getLayoutStructure());
        assertNull(row.getItemCardStyle());
        assertNull(row.getShowImages());
        assertNull(row.getImagePosition());
        assertNull(row.getImageAspectRatio());
        assertNull(row.getFontFamily());
        assertNull(row.getHeaderAlignment());
        assertNull(row.getShowCoverImage());
    }

    @Test
    void normalisedFillsEveryNullStructuralField() {
        MenuTemplateEntity row = legacyRow("CLASSIC").normalised();

        assertEquals(LayoutStructure.SINGLE_COLUMN, row.getLayoutStructure());
        assertEquals(ItemCardStyle.CARD_BORDERED, row.getItemCardStyle());
        assertEquals(Boolean.TRUE, row.getShowImages());
        assertEquals(ImagePosition.LEFT, row.getImagePosition());
        assertEquals(ImageAspectRatio.SQUARE_1_1, row.getImageAspectRatio());
        assertEquals(FontFamily.SANS_SERIF, row.getFontFamily());
        assertEquals(HeaderAlignment.CENTER, row.getHeaderAlignment());
        assertEquals(Boolean.TRUE, row.getShowCoverImage());
    }

    @Test
    void normalisedMatchesTheMigrationsBackfill() {
        // The SQL backfill and normalised() must agree, or a menu would change
        // appearance the moment the migration ran. These are the values in
        // db/manual/001-menu-template-structure.sql step 2.
        MenuTemplateEntity row = legacyRow("ANY").normalised();

        assertEquals("SINGLE_COLUMN", row.getLayoutStructure().name());
        assertEquals("CARD_BORDERED", row.getItemCardStyle().name());
        assertEquals("LEFT", row.getImagePosition().name());
        assertEquals("SQUARE_1_1", row.getImageAspectRatio().name());
        assertEquals("SANS_SERIF", row.getFontFamily().name());
        assertEquals("CENTER", row.getHeaderAlignment().name());
        assertTrue(row.getShowImages());
        assertTrue(row.getShowCoverImage());
    }

    @Test
    void normalisedLeavesAlreadyPopulatedFieldsAlone() {
        MenuTemplateEntity row = MenuTemplateEntity.builder()
                .key("MODERN_DARK").displayName("Modern Dark")
                .backgroundMode(MenuTemplateEntity.BackgroundMode.DARK)
                .accentToken(MenuTemplateEntity.AccentToken.BRAND)
                .layoutStructure(LayoutStructure.GRID_3)
                .itemCardStyle(ItemCardStyle.ELEVATED_SHADOW)
                .showImages(false)
                .imagePosition(ImagePosition.RIGHT)
                .imageAspectRatio(ImageAspectRatio.LANDSCAPE_16_9)
                .fontFamily(FontFamily.MODERN_MONO)
                .headerAlignment(HeaderAlignment.LEFT)
                .showCoverImage(false)
                .build()
                .normalised();

        assertEquals(LayoutStructure.GRID_3, row.getLayoutStructure());
        assertEquals(ItemCardStyle.ELEVATED_SHADOW, row.getItemCardStyle());
        assertEquals(Boolean.FALSE, row.getShowImages());
        assertEquals(ImagePosition.RIGHT, row.getImagePosition());
        assertEquals(ImageAspectRatio.LANDSCAPE_16_9, row.getImageAspectRatio());
        assertEquals(FontFamily.MODERN_MONO, row.getFontFamily());
        assertEquals(HeaderAlignment.LEFT, row.getHeaderAlignment());
        assertEquals(Boolean.FALSE, row.getShowCoverImage());
    }

    // ---- imagesVisible() -------------------------------------------------

    @Test
    void imagesAreHiddenWhenTheToggleIsOffEvenIfAPositionIsSet() {
        MenuTemplateEntity row = legacyRow("X").normalised();
        row.setShowImages(false);
        row.setImagePosition(ImagePosition.LEFT);

        assertFalse(row.imagesVisible());
    }

    @Test
    void imagesAreHiddenWhenThePositionIsNoneEvenIfTheToggleIsOn() {
        MenuTemplateEntity row = legacyRow("X").normalised();
        row.setShowImages(true);
        row.setImagePosition(ImagePosition.NONE);

        assertFalse(row.imagesVisible());
    }

    @Test
    void imagesAreVisibleOnlyWhenBothSettingsAgree() {
        MenuTemplateEntity row = legacyRow("X").normalised();
        row.setShowImages(true);
        row.setImagePosition(ImagePosition.TOP);

        assertTrue(row.imagesVisible());
    }

    @Test
    void imagesVisibleToleratesALegacyRowWithNulls() {
        // A pre-migration row must not throw here - it is the customer page's
        // first call on every render.
        assertTrue(legacyRow("X").imagesVisible());
    }

    // ---- create ----------------------------------------------------------

    @Test
    void createDefaultsEveryOmittedStructuralField() {
        when(repository.existsById("RUSTIC")).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MenuTemplateEntity created = service.create(CreateMenuTemplateRequest.builder()
                .key("RUSTIC").displayName("Rustic")
                .backgroundMode(MenuTemplateEntity.BackgroundMode.TINTED)
                .accentToken(MenuTemplateEntity.AccentToken.INFO)
                .build());

        assertEquals(LayoutStructure.SINGLE_COLUMN, created.getLayoutStructure());
        assertEquals(ItemCardStyle.CARD_BORDERED, created.getItemCardStyle());
        assertEquals(Boolean.TRUE, created.getShowImages());
        assertEquals(ImagePosition.LEFT, created.getImagePosition());
        assertEquals(ImageAspectRatio.SQUARE_1_1, created.getImageAspectRatio());
        assertEquals(FontFamily.SANS_SERIF, created.getFontFamily());
        assertEquals(HeaderAlignment.CENTER, created.getHeaderAlignment());
        assertEquals(Boolean.TRUE, created.getShowCoverImage());
    }

    @Test
    void createNeverPersistsANullStructuralField() {
        when(repository.existsById("RUSTIC")).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create(CreateMenuTemplateRequest.builder()
                .key("RUSTIC").displayName("Rustic")
                .backgroundMode(MenuTemplateEntity.BackgroundMode.LIGHT)
                .accentToken(MenuTemplateEntity.AccentToken.BRAND)
                .build());

        ArgumentCaptor<MenuTemplateEntity> captor = ArgumentCaptor.forClass(MenuTemplateEntity.class);
        verify(repository).save(captor.capture());
        MenuTemplateEntity saved = captor.getValue();
        assertAll(
                () -> assertNotNull(saved.getLayoutStructure()),
                () -> assertNotNull(saved.getItemCardStyle()),
                () -> assertNotNull(saved.getShowImages()),
                () -> assertNotNull(saved.getImagePosition()),
                () -> assertNotNull(saved.getImageAspectRatio()),
                () -> assertNotNull(saved.getFontFamily()),
                () -> assertNotNull(saved.getHeaderAlignment()),
                () -> assertNotNull(saved.getShowCoverImage()));
    }

    @Test
    void createHonoursEverySuppliedStructuralField() {
        when(repository.existsById("PHOTO")).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MenuTemplateEntity created = service.create(CreateMenuTemplateRequest.builder()
                .key("PHOTO").displayName("Photo Grid")
                .backgroundMode(MenuTemplateEntity.BackgroundMode.DARK)
                .accentToken(MenuTemplateEntity.AccentToken.DANGER)
                .layoutStructure(LayoutStructure.GRID_3)
                .itemCardStyle(ItemCardStyle.ELEVATED_SHADOW)
                .showImages(true)
                .imagePosition(ImagePosition.TOP)
                .imageAspectRatio(ImageAspectRatio.LANDSCAPE_16_9)
                .fontFamily(FontFamily.MODERN_MONO)
                .headerAlignment(HeaderAlignment.LEFT)
                .showCoverImage(false)
                .build());

        assertEquals(LayoutStructure.GRID_3, created.getLayoutStructure());
        assertEquals(ItemCardStyle.ELEVATED_SHADOW, created.getItemCardStyle());
        assertEquals(ImagePosition.TOP, created.getImagePosition());
        assertEquals(ImageAspectRatio.LANDSCAPE_16_9, created.getImageAspectRatio());
        assertEquals(FontFamily.MODERN_MONO, created.getFontFamily());
        assertEquals(HeaderAlignment.LEFT, created.getHeaderAlignment());
        assertEquals(Boolean.FALSE, created.getShowCoverImage());
    }

    @Test
    void createAcceptsAnExplicitlyImagelessTemplate() {
        when(repository.existsById("TEXT_ONLY")).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MenuTemplateEntity created = service.create(CreateMenuTemplateRequest.builder()
                .key("TEXT_ONLY").displayName("Text Only")
                .backgroundMode(MenuTemplateEntity.BackgroundMode.LIGHT)
                .accentToken(MenuTemplateEntity.AccentToken.WARN)
                .showImages(false)
                .imagePosition(ImagePosition.NONE)
                .build());

        assertEquals(Boolean.FALSE, created.getShowImages());
        assertFalse(created.imagesVisible());
    }

    // ---- update ----------------------------------------------------------

    @Test
    void updateLeavesOmittedStructuralFieldsUnchanged() {
        // The regression this guards: a client that predates these fields
        // saves a rename, and the template's whole layout is blanked.
        MenuTemplateEntity existing = MenuTemplateEntity.builder()
                .key("MODERN_DARK").displayName("Modern Dark")
                .backgroundMode(MenuTemplateEntity.BackgroundMode.DARK)
                .accentToken(MenuTemplateEntity.AccentToken.BRAND)
                .layoutStructure(LayoutStructure.GRID_2)
                .itemCardStyle(ItemCardStyle.ELEVATED_SHADOW)
                .showImages(true)
                .imagePosition(ImagePosition.TOP)
                .imageAspectRatio(ImageAspectRatio.LANDSCAPE_16_9)
                .fontFamily(FontFamily.SANS_SERIF)
                .headerAlignment(HeaderAlignment.CENTER)
                .showCoverImage(true)
                .build();
        when(repository.findById("MODERN_DARK")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MenuTemplateEntity updated = service.update("MODERN_DARK", UpdateMenuTemplateRequest.builder()
                .displayName("Midnight")
                .backgroundMode(MenuTemplateEntity.BackgroundMode.DARK)
                .accentToken(MenuTemplateEntity.AccentToken.BRAND)
                .build());

        assertEquals("Midnight", updated.getDisplayName());
        assertEquals(LayoutStructure.GRID_2, updated.getLayoutStructure());
        assertEquals(ItemCardStyle.ELEVATED_SHADOW, updated.getItemCardStyle());
        assertEquals(ImagePosition.TOP, updated.getImagePosition());
        assertEquals(ImageAspectRatio.LANDSCAPE_16_9, updated.getImageAspectRatio());
        assertEquals(HeaderAlignment.CENTER, updated.getHeaderAlignment());
        assertEquals(Boolean.TRUE, updated.getShowCoverImage());
    }

    @Test
    void updateAppliesEverySuppliedStructuralField() {
        MenuTemplateEntity existing = legacyRow("CLASSIC");
        when(repository.findById("CLASSIC")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MenuTemplateEntity updated = service.update("CLASSIC", UpdateMenuTemplateRequest.builder()
                .displayName("Classic")
                .backgroundMode(MenuTemplateEntity.BackgroundMode.LIGHT)
                .accentToken(MenuTemplateEntity.AccentToken.BRAND)
                .layoutStructure(LayoutStructure.TWO_COLUMN)
                .itemCardStyle(ItemCardStyle.MINIMAL_DIVIDER)
                .showImages(false)
                .imagePosition(ImagePosition.NONE)
                .imageAspectRatio(ImageAspectRatio.ROUNDED_AVATAR)
                .fontFamily(FontFamily.SERIF)
                .headerAlignment(HeaderAlignment.LEFT)
                .showCoverImage(false)
                .build());

        assertEquals(LayoutStructure.TWO_COLUMN, updated.getLayoutStructure());
        assertEquals(ItemCardStyle.MINIMAL_DIVIDER, updated.getItemCardStyle());
        assertEquals(Boolean.FALSE, updated.getShowImages());
        assertEquals(ImagePosition.NONE, updated.getImagePosition());
        assertEquals(ImageAspectRatio.ROUNDED_AVATAR, updated.getImageAspectRatio());
        assertEquals(FontFamily.SERIF, updated.getFontFamily());
        assertEquals(HeaderAlignment.LEFT, updated.getHeaderAlignment());
        assertEquals(Boolean.FALSE, updated.getShowCoverImage());
        assertFalse(updated.imagesVisible());
    }

    @Test
    void updateNormalisesALegacyRowEvenWhenNoStructureIsSupplied() {
        MenuTemplateEntity existing = legacyRow("CLASSIC");
        when(repository.findById("CLASSIC")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MenuTemplateEntity updated = service.update("CLASSIC", UpdateMenuTemplateRequest.builder()
                .displayName("Renamed")
                .backgroundMode(MenuTemplateEntity.BackgroundMode.LIGHT)
                .accentToken(MenuTemplateEntity.AccentToken.BRAND)
                .build());

        // Saving a legacy row is the opportunity to complete it, so it stops
        // depending on read-time normalisation.
        assertNotNull(updated.getLayoutStructure());
        assertNotNull(updated.getImagePosition());
        assertNotNull(updated.getFontFamily());
    }

    // ---- getAll ----------------------------------------------------------

    @Test
    void getAllNormalisesLegacyRows() {
        when(repository.findAll()).thenReturn(List.of(legacyRow("CLASSIC"), legacyRow("VIBRANT")));

        List<MenuTemplateEntity> all = service.getAll();

        assertEquals(2, all.size());
        assertTrue(all.stream().allMatch(t -> t.getLayoutStructure() != null));
        assertTrue(all.stream().allMatch(t -> t.getFontFamily() != null));
        assertTrue(all.stream().allMatch(t -> t.getShowCoverImage() != null));
    }

    // ---- seeded starters -------------------------------------------------

    @Test
    void seededStartersAreStructurallyDistinctNotJustRecoloured() {
        when(repository.count()).thenReturn(0L);
        service.seedDefaults();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MenuTemplateEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        List<MenuTemplateEntity> seeded = captor.getValue();

        Set<LayoutStructure> layouts = seeded.stream()
                .map(MenuTemplateEntity::getLayoutStructure).collect(Collectors.toSet());
        Set<ItemCardStyle> cards = seeded.stream()
                .map(MenuTemplateEntity::getItemCardStyle).collect(Collectors.toSet());

        assertEquals(4, layouts.size(), "each starter should demonstrate a different layout");
        assertEquals(4, cards.size(), "each starter should demonstrate a different card style");
    }

    @Test
    void everySeededStarterIsFullyDefined() {
        when(repository.count()).thenReturn(0L);
        service.seedDefaults();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MenuTemplateEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());

        for (MenuTemplateEntity t : captor.getValue()) {
            assertAll("starter " + t.getKey(),
                    () -> assertNotNull(t.getLayoutStructure()),
                    () -> assertNotNull(t.getItemCardStyle()),
                    () -> assertNotNull(t.getShowImages()),
                    () -> assertNotNull(t.getImagePosition()),
                    () -> assertNotNull(t.getImageAspectRatio()),
                    () -> assertNotNull(t.getFontFamily()),
                    () -> assertNotNull(t.getHeaderAlignment()),
                    () -> assertNotNull(t.getShowCoverImage()));
        }
    }

    @Test
    void oneSeededStarterExercisesTheNoImagePath() {
        // If every starter showed images, a fresh install would never render
        // the imageless layout and it would rot untested in production.
        when(repository.count()).thenReturn(0L);
        service.seedDefaults();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MenuTemplateEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());

        assertTrue(captor.getValue().stream().anyMatch(t -> !t.imagesVisible()),
                "expected at least one starter with images off");
        assertTrue(captor.getValue().stream().anyMatch(MenuTemplateEntity::imagesVisible),
                "expected at least one starter with images on");
    }
}
