package trc.core.settings;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.layouts.SpacerElement;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import trc.core.TRCCore;
import trc.core.TRCCoreClient;
import trc.core.TRCCoreClientSettings;
import trc.core.TRCCoreSettings;
import trc.core.net.ClientSettingsCache;
import trc.core.net.SettingsSnapshot;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class TRCGlobalSettingsScreen extends Screen implements ClientSettingsCache.Listener {

    private static final int WIDGET_WIDTH = 250;
    private static final int WIDGET_HEIGHT = 20;
    private static final int SPACING = 4;
    private static final int TITLE_SPACING = 2;
    private static final int LABEL_SPACING = 1;
    private static final int SECTION_SPACING = 8;
    private static final int MOD_SPACING = 16;
    private static final String CLIENT_SUFFIX = "_client";
    private static final int HEADER_HEIGHT = 33;
    private static final int SEARCH_BUTTON_SIZE = 20;
    private static final Identifier SEARCH_SPRITE = Identifier.withDefaultNamespace("icon/search");
    private static final int RESET_SIZE = 20;
    private static final int ROW_GAP = 4;
    private static final int MAIN_WIDTH = WIDGET_WIDTH - RESET_SIZE - ROW_GAP;
    private static final Identifier RESET_SPRITE = Identifier.fromNamespaceAndPath(TRCCore.MOD_ID, "icon/reset");
    private static final int RESET_ICON_SIZE = 15;
    private static final int FILTER_ICON_SIZE = 16;

    private static SideFilter sideFilter = SideFilter.BOTH;
    private final List<SettingBox> inputFields = new ArrayList<>();
    private final List<Runnable> valueRefreshers = new ArrayList<>();
    private @Nullable StringWidget titleWidget;
    private @Nullable SpriteIconButton searchButton;
    private final Map<SideFilter, SpriteIconButton> filterButtons = new EnumMap<>(SideFilter.class);
    private @Nullable EditBox searchBox;
    private boolean searchOpen;
    private String query = "";
    private boolean serverSectionsAllowed;
    private boolean awaitingRefresh;
    private boolean searchToggledByClick;
    private boolean opened;
    private @Nullable KeyCapture capture;
    private boolean refreshing;
    private @Nullable ScrollableLayout bodyScroll;
    private @Nullable AbstractWidget bodyContainer;

    public TRCGlobalSettingsScreen(Component title) {
        super(title);
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.bodyContainer = null;

        TRCCoreClientSettings.localize();

        ClientSettingsCache.setListener(this);

        this.serverSectionsAllowed = !ClientSettingsCache.sections().isEmpty();

        if (!this.opened) {
            this.opened = true;

            this.awaitingRefresh = ClientSettingsCache.serverHasCore();
            ClientSettingsCache.requestRefresh();
        }

        this.titleWidget = this.addRenderableWidget(new StringWidget(this.screenTitle(), this.font));

        this.searchButton = this.addRenderableWidget(
                SpriteIconButton.builder(Component.translatable("menu.trc-core.search"), _ -> this.toggleSearch(), true)
                        .size(SEARCH_BUTTON_SIZE, SEARCH_BUTTON_SIZE)
                        .sprite(SEARCH_SPRITE, 15, 15)
                        .build());

        this.filterButtons.clear();

        for (SideFilter filter : SideFilter.values()) {
            SpriteIconButton button = this.addRenderableWidget(
                    SpriteIconButton.builder(filter.tooltip, _ -> this.cycleSideFilter(), true)
                            .size(SEARCH_BUTTON_SIZE, SEARCH_BUTTON_SIZE)
                            .sprite(filter.sprite, FILTER_ICON_SIZE, FILTER_ICON_SIZE)
                            .build());

            button.setTooltip(Tooltip.create(filter.tooltip));
            this.filterButtons.put(filter, button);
        }

        this.updateFilterButtons();

        this.rebuildBody();
        this.repositionElements();
    }

    @Override
    public void removed() {
        this.capture = null;

        for (SettingBox box : this.inputFields) {
            box.commitIfDirty();
        }

        ClientSettingsCache.setListener(null);
        super.removed();
    }

    private static MutableComponent label(Setting s) {
        boolean named = s.name != null && !s.name.isBlank();
        if (s.translationComponent == null || named && !isTranslated(s.translationComponent)) {
            return Component.literal(named ? s.name : s.id);
        }
        return s.translationComponent.copy();
    }

    private static boolean isTranslated(Component c) {
        return !(c.getContents() instanceof TranslatableContents t) || Language.getInstance().has(t.getKey());
    }
    private @NotNull Component screenTitle() {
        Component base = Component.translatable("menu.trc-core.settings");

        boolean connected = this.minecraft != null && this.minecraft.getConnection() != null;

        if (connected && !ClientSettingsCache.serverHasCore()) {
            return base.copy().append(Component.translatable("menu.trc-core.no_server_core")
                    .withStyle(ChatFormatting.GRAY));
        }

        return base;
    }


    @Override
    public void onSettingsSynced(boolean structureChanged) {
        boolean wasAllowed = this.serverSectionsAllowed;

        if (this.awaitingRefresh) {
            this.awaitingRefresh = false;
            this.serverSectionsAllowed = !ClientSettingsCache.sections().isEmpty();
        } else if (ClientSettingsCache.sections().isEmpty()) {
            this.serverSectionsAllowed = false;
        }

        this.updateFilterButtons();

        if (!this.serverSectionsAllowed) {
            if (wasAllowed) {
                this.rebuildBody();
                this.repositionElements();
            }

            return;
        }

        if (!structureChanged) {
            for (Runnable refresher : this.valueRefreshers) {
                refresher.run();
            }

            return;
        }

        this.rebuildBody();
        this.repositionElements();

        if (this.searchBox != null) {
            this.focusSearch();
        }
    }


    private void toggleSearch() {
        this.query = "";
        this.searchToggledByClick = true;

        if (this.searchOpen) {
            this.searchOpen = false;
            this.searchBox = null;
        } else {
            this.searchOpen = true;
            this.searchBox = new EditBox(this.font, WIDGET_WIDTH, WIDGET_HEIGHT,
                    Component.translatable("menu.trc-core.search"));
            this.searchBox.setResponder(this::onQueryChanged);
        }

        this.rebuildBody();

        if (this.searchBox != null) {
            this.focusSearch();
        }
    }

    private void onQueryChanged(@NotNull String text) {
        if (text.equals(this.query)) {
            return;
        }

        this.query = text;

        this.rebuildBody();
        this.focusSearch();
    }

    private void focusSearch() {
        if (this.searchBox == null || this.bodyContainer == null) {
            return;
        }

        this.setFocused(this.bodyContainer);

        if (this.bodyContainer instanceof ContainerEventHandler container) {
            container.setFocused(this.searchBox);
        }
    }


    private void rebuildBody() {
        double scroll = this.bodyContainer instanceof AbstractScrollArea area ? area.scrollAmount() : 0.0;

        if (this.bodyContainer != null) {
            this.removeWidget(this.bodyContainer);
            this.bodyContainer = null;
        }

        this.inputFields.clear();
        this.valueRefreshers.clear();
        this.capture = null;

        LinearLayout body = LinearLayout.vertical().spacing(SPACING);
        body.defaultCellSetting().alignHorizontallyCenter();

        if (this.searchOpen && this.searchBox != null) {
            body.addChild(labelled(this.searchBox, Component.translatable("menu.trc-core.search")));
            body.addChild(SpacerElement.height(SECTION_SPACING));
        }

        boolean firstMod = true;

        for (Map.Entry<String, List<Section>> mod : collectSections().entrySet()) {
            if (!firstMod) {
                body.addChild(SpacerElement.height(MOD_SPACING));
            }

            firstMod = false;
            boolean firstSection = true;
            Component lastTitle = null;

            for (Section section : mod.getValue()) {
                boolean continues = section.title().equals(lastTitle);

                if (!firstSection && !continues) {
                    body.addChild(SpacerElement.height(SECTION_SPACING));
                }

                firstSection = false;
                lastTitle = section.title();

                if (!continues) {
                    body.addChild(new StringWidget(section.title(), this.font));
                    body.addChild(SpacerElement.height(TITLE_SPACING));
                }

                this.addSettings(body, section);
            }
        }

        this.bodyScroll = new ScrollableLayout(this.minecraft, body, this.contentHeight());
        this.bodyScroll.setMinWidth(WIDGET_WIDTH);
        this.bodyScroll.visitWidgets(widget -> {
            this.bodyContainer = widget;
            this.addRenderableWidget(widget);
        });

        this.positionBody();

        if (this.bodyContainer instanceof AbstractScrollArea area) {
            area.setScrollAmount(scroll);
        }
    }

    private void positionBody() {
        if (this.bodyScroll == null) {
            return;
        }

        this.bodyScroll.arrangeElements();
        this.bodyScroll.setMaxHeight(this.contentHeight());
        this.bodyScroll.setX((this.width - this.bodyScroll.getWidth()) / 2);
        this.bodyScroll.setY(HEADER_HEIGHT);
    }

    private int contentHeight() {
        return Math.max(WIDGET_HEIGHT, this.height - HEADER_HEIGHT);
    }

    @Override
    protected void repositionElements() {
        if (this.titleWidget != null) {
            this.titleWidget.setMessage(this.screenTitle());
            this.titleWidget.setPosition(
                    (this.width - this.titleWidget.getWidth()) / 2,
                    (HEADER_HEIGHT - this.font.lineHeight) / 2);
        }

        for (SpriteIconButton button : this.filterButtons.values()) {
            button.setPosition(
                    (this.width - WIDGET_WIDTH) / 2,
                    (HEADER_HEIGHT - SEARCH_BUTTON_SIZE) / 2);
        }

        if (this.searchButton != null) {
            this.searchButton.setPosition(
                    (this.width + WIDGET_WIDTH) / 2 - SEARCH_BUTTON_SIZE,
                    (HEADER_HEIGHT - SEARCH_BUTTON_SIZE) / 2);
        }

        this.positionBody();
    }


    private record SettingSink(Consumer<Setting> preview, Consumer<Setting> commit, Predicate<Setting> resettable) {

        static @NotNull SettingSink local(@NotNull ISettingsHolder holder) {
            return new SettingSink(_ -> holder.onSettingsChanged(), _ -> commitLocal(holder),
                    setting -> !setting.isDefault());
        }

        static @NotNull SettingSink remote(SettingsSnapshot.@NotNull Section section) {
            return new SettingSink(
                    setting -> ClientSettingsCache.preview(section.modId, setting),
                    setting -> ClientSettingsCache.edit(section.modId, setting),
                    setting -> SettingsAudit.isSensitive(setting)
                            ? section.hasHiddenChange(setting.id)
                            : !setting.isDefault());
        }
    }

    private record KeyCapture(Button button, KeySetting setting, SettingSink sink) {}

    private record Section(Component title, List<Setting> settings, SettingSink sink, @Nullable String serverModId) {
    }

    private record RawSection(String modId, String displayName, boolean client, List<Setting> settings,
                              SettingSink sink, boolean bothSides) {
    }

    private enum SideFilter {
        BOTH("both"),
        SERVER("server"),
        CLIENT("client");

        final Identifier sprite;
        final Component tooltip;

        SideFilter(String name) {
            this.sprite = Identifier.fromNamespaceAndPath(TRCCore.MOD_ID, "icon/filter_" + name);
            this.tooltip = Component.translatable("menu.trc-core.filter." + name);
        }

        @NotNull SideFilter next() {
            return values()[(this.ordinal() + 1) % values().length];
        }

        boolean keeps(@NotNull RawSection raw) {
            return switch (this) {
                case BOTH -> true;
                case SERVER -> !raw.client() || raw.bothSides();
                case CLIENT -> raw.client() || raw.bothSides();
            };
        }
    }

    private void cycleSideFilter() {
        sideFilter = sideFilter.next();

        this.updateFilterButtons();
        this.rebuildBody();

        if (this.searchBox != null) {
            this.focusSearch();
        }
    }

    private void updateFilterButtons() {
        for (Map.Entry<SideFilter, SpriteIconButton> entry : this.filterButtons.entrySet()) {
            boolean shown = this.serverSectionsAllowed && entry.getKey() == sideFilter;

            entry.getValue().visible = shown;
            entry.getValue().active = shown;
        }
    }

    private @NotNull Map<String, List<Section>> collectSections() {
        Map<String, List<Section>> sections = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        String needle = this.query.trim().toLowerCase(Locale.ROOT);

        SideFilter sides = this.serverSectionsAllowed ? sideFilter : SideFilter.BOTH;

        for (RawSection raw : rawSections()) {
            if (!sides.keeps(raw)) {
                continue;
            }

            List<Setting> visible = filter(raw, needle);

            if (visible.isEmpty()) {
                continue;
            }

            sections.computeIfAbsent(sortKey(raw), _ -> new ArrayList<>())
                    .add(new Section(sectionTitle(raw.displayName(), raw.client()), visible, raw.sink(),
                            raw.client() ? null : raw.modId()));
        }

        return new LinkedHashMap<>(sections);
    }

    private static @NotNull String sortKey(@NotNull RawSection raw) {
        String group = TRCCore.MOD_ID.equals(raw.modId()) ? "1" : "0";
        return group + raw.displayName() + '\n' + raw.modId();
    }

    private @NotNull List<RawSection> rawSections() {
        List<RawSection> raw = new ArrayList<>();

        String coreName = Language.getInstance().getOrDefault("trc-core.section.name", displayName(TRCCore.MOD_ID));

        for (SettingsSnapshot.Section section : this.serverSectionsAllowed
                ? ClientSettingsCache.sections()
                : List.<SettingsSnapshot.Section>of()) {
            if (section.settings == null || section.settings.isEmpty()) {
                continue;
            }

            if (TRCCore.MOD_ID.equals(section.modId)) coreName = section.displayName;

            raw.add(new RawSection(section.modId, section.displayName, false, section.settings,
                    SettingSink.remote(section), false));
        }

        if (this.serverSectionsAllowed) {
            raw.add(new RawSection(TRCCore.MOD_ID, coreName, false, TRCCoreClientSettings.INSTANCE.getSettings(),
                    SettingSink.local(TRCCoreClientSettings.INSTANCE), true));
        }

        for (Map.Entry<String, ISettingsHolder> entry : SettingsManager.getSettingsManager().getRegisteredMods().entrySet()) {
            if (!entry.getKey().endsWith(CLIENT_SUFFIX)) {
                continue;
            }

            if (TRCCoreClientSettings.MOD_ID.equals(entry.getKey())) {
                continue;
            }

            ISettingsHolder holder = entry.getValue();

            if (holder.getSettings().isEmpty()) {
                continue;
            }

            String modId = entry.getKey().substring(0, entry.getKey().length() - CLIENT_SUFFIX.length());
            raw.add(new RawSection(modId, displayName(modId), true, holder.getSettings(), SettingSink.local(holder), false));
        }

        return raw;
    }

    private static @NotNull List<Setting> filter(@NotNull RawSection raw, @NotNull String needle) {
        if (needle.isEmpty()) {
            return raw.settings();
        }

        boolean side = raw.bothSides()
                ? contains("client", needle) || contains("server", needle)
                : contains(raw.client() ? "client" : "server", needle);

        boolean wholeSection = contains(raw.displayName(), needle)
                || contains(raw.modId(), needle)
                || side;

        if (wholeSection) {
            return raw.settings();
        }

        List<Setting> matches = new ArrayList<>();

        for (Setting setting : raw.settings()) {
            if (contains(label(setting).getString(), needle) || contains(valueText(setting), needle)) {
                matches.add(setting);
            }
        }

        return matches;
    }

    @Contract(pure = true)
    private static boolean contains(@Nullable String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static @NotNull String valueText(@NotNull Setting setting) {
        return switch (setting) {
            case BoolSetting bool -> bool.value ? "on" : "off";
            case IntSetting number -> String.valueOf(number.value);
            case ChoiceSetting choice -> hasChoice(choice) ? choice.values.get(choice.index) : "";
            case StringSetting text -> text.sensitive ? "" : text.value;
            case KeySetting key -> key.value + " " + keyDisplay(key).getString();
            default -> "";
        };
    }

    private static int maxInputLength(@NotNull IntSetting setting) {

        return Math.max(
                String.valueOf(setting.minValue).length(),
                String.valueOf(setting.maxValue).length()
        );
    }

    private static @NotNull Component sectionTitle(String displayName, boolean client) {
        MutableComponent name = client
                ? Component.translatable("menu.trc-core.section_client", displayName)
                : Component.literal(displayName);

        return name.withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
    }

    private static @NotNull String displayName(String modId) {
        return FabricLoader.getInstance()
                .getModContainer(modId)
                .map(container -> container.getMetadata().getName())
                .orElse(modId);
    }


    private void addSettings(LinearLayout body, @NotNull Section section) {
        SettingSink sink = section.sink();

        for (Setting setting : section.settings()) {

            if (setting instanceof BoolSetting boolSetting) {
                Button button = Button.builder(
                                getBoolButtonText(boolSetting),
                                pressed -> {
                                    boolSetting.value = !boolSetting.value;
                                    pressed.setMessage(getBoolButtonText(boolSetting));
                                    sink.commit().accept(boolSetting);
                                    this.refreshValues();
                                }
                        )
                        .width(MAIN_WIDTH)
                        .build();

                this.valueRefreshers.add(() -> button.setMessage(getBoolButtonText(boolSetting)));
                body.addChild(withReset(button, boolSetting, sink,
                        () -> button.setMessage(getBoolButtonText(boolSetting))));
            }
            else if (setting instanceof ChoiceSetting choiceSetting) {
                Button button = Button.builder(
                                getChoiceButtonText(choiceSetting),
                                pressed -> {
                                    if (choiceSetting.values != null && !choiceSetting.values.isEmpty()) {
                                        choiceSetting.index = (choiceSetting.index + 1) % choiceSetting.values.size();
                                    }
                                    pressed.setMessage(getChoiceButtonText(choiceSetting));
                                    sink.commit().accept(choiceSetting);
                                    this.refreshValues();
                                }
                        )
                        .width(MAIN_WIDTH)
                        .build();

                this.valueRefreshers.add(() -> button.setMessage(getChoiceButtonText(choiceSetting)));
                body.addChild(withReset(button, choiceSetting, sink,
                        () -> button.setMessage(getChoiceButtonText(choiceSetting))));
            }
            else if (setting instanceof StringSetting stringSetting) {
                SettingBox box = new SettingBox(this.font, MAIN_WIDTH, WIDGET_HEIGHT,
                        label(stringSetting),
                        () -> sink.commit().accept(stringSetting), false);

                Supplier<String> shown = () -> stringSetting.sensitive ? "" : stringSetting.value;

                box.setMaxLength(stringSetting.maxLength);
                box.setValue(shown.get());

                if (stringSetting.sensitive) {
                    box.setHint(Component.translatable("menu.trc-core.hidden_hint").withStyle(ChatFormatting.DARK_GRAY));
                }

                box.setResponder(text -> {
                    if (box.isApplying()) return;

                    stringSetting.value = text;
                    box.markDirty();
                    sink.preview().accept(stringSetting);
                    this.refreshValues();
                });

                this.inputFields.add(box);
                this.valueRefreshers.add(() -> {
                    if (!box.isFocused()) box.setValueQuietly(shown.get());
                });

                body.addChild(labelled(
                        withReset(box, stringSetting, sink, () -> box.setValueQuietly(shown.get())),
                        label(stringSetting)));
            }
            else if (setting instanceof KeySetting keySetting) {
                Button button = Button.builder(keyText(keySetting), pressed -> {
                    this.capture = new KeyCapture(pressed, keySetting, sink);
                    pressed.setMessage(label(keySetting).append(": ")
                            .append(Component.translatable("menu.trc-core.key_waiting").withStyle(ChatFormatting.YELLOW)));
                }).width(MAIN_WIDTH).build();

                this.valueRefreshers.add(() -> {
                    if (this.capture == null || this.capture.button() != button) {
                        button.setMessage(keyText(keySetting));
                    }
                });

                body.addChild(withReset(button, keySetting, sink, () -> button.setMessage(keyText(keySetting))));
            }
            else if (setting instanceof IntSetting intSetting) {
                if (intSetting.isInputField) {
                    SettingBox box = new SettingBox(this.font, MAIN_WIDTH, WIDGET_HEIGHT,
                            label(intSetting),
                            () -> sink.commit().accept(intSetting), true);

                    box.setMaxLength(maxInputLength(intSetting));
                    box.setValue(String.valueOf(intSetting.value));
                    box.setResponder(text -> {
                        if (box.isApplying()) return;

                        try {
                            int parsed = Integer.parseInt(text.trim());
                            intSetting.value = Math.clamp(parsed, intSetting.minValue, intSetting.maxValue);
                            box.markDirty();
                            sink.preview().accept(intSetting);
                            this.refreshValues();
                        } catch (NumberFormatException ignored) {}
                    });

                    this.inputFields.add(box);

                    this.valueRefreshers.add(() -> {
                        if (!box.isFocused()) {
                            box.setValueQuietly(String.valueOf(intSetting.value));
                        }
                    });

                    LinearLayout row = withReset(box, intSetting, sink,
                            () -> box.setValueQuietly(String.valueOf(intSetting.value)));

                    body.addChild(labelled(row, label(intSetting)));
                } else {
                    IntSlider slider = new IntSlider(
                            0, 0, MAIN_WIDTH, WIDGET_HEIGHT, intSetting, section.serverModId(),
                            () -> sink.preview().accept(intSetting),
                            () -> {
                                sink.commit().accept(intSetting);
                                this.refreshValues();
                            }
                    );

                    this.valueRefreshers.add(() -> {
                        if (!slider.isHeld()) {
                            slider.refresh();
                        }
                    });

                    body.addChild(withReset(slider, intSetting, sink, slider::refresh));
                }
            }
        }
    }

    private @NotNull LinearLayout withReset(@NotNull AbstractWidget main, @NotNull Setting setting,
                                            @NotNull SettingSink sink, @NotNull Runnable redrawMain) {
        SpriteIconButton reset = SpriteIconButton.builder(
                        Component.translatable("menu.trc-core.reset"),
                        _ -> {
                            setting.reset();
                            redrawMain.run();
                            sink.commit().accept(setting);
                            this.refreshValues();
                        }, true)
                .size(RESET_SIZE, RESET_SIZE)
                .sprite(RESET_SPRITE, RESET_ICON_SIZE, RESET_ICON_SIZE)
                .build();

        reset.active = sink.resettable().test(setting);
        reset.setTooltip(Tooltip.create(Component.translatable("menu.trc-core.reset")));

        this.valueRefreshers.add(() -> reset.active = sink.resettable().test(setting));

        LinearLayout row = LinearLayout.horizontal().spacing(ROW_GAP);
        row.defaultCellSetting().alignVerticallyMiddle();
        row.addChild(main);
        row.addChild(reset);

        return row;
    }

    private void refreshValues() {
        if (this.refreshing) return;

        this.refreshing = true;
        try {
            for (Runnable refresher : this.valueRefreshers) {
                refresher.run();
            }
        } finally {
            this.refreshing = false;
        }
    }

    private @NotNull LinearLayout labelled(@NotNull LayoutElement field, @NotNull Component label) {
        LinearLayout wrapper = LinearLayout.vertical().spacing(LABEL_SPACING);
        wrapper.defaultCellSetting().alignHorizontallyLeft();
        wrapper.addChild(new StringWidget(label.copy().withStyle(ChatFormatting.WHITE), this.font));
        wrapper.addChild(field);
        return wrapper;
    }


    @Override
    public boolean keyPressed(final @NonNull KeyEvent event) {
        if (this.capture != null) {
            finishCapture(event.isEscape() ? KeySetting.UNBOUND : InputConstants.getKey(event).getName());
            return true;
        }

        if (super.keyPressed(event)) {
            this.searchToggledByClick = false;
            return true;
        }

        if (TRCCoreClient.settingsKey != null && !this.isEditingInput() && TRCCoreClient.settingsKey.matches(event)) {
            this.onClose();
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseClicked(final @NonNull MouseButtonEvent event, final boolean doubleClick) {
        if (this.capture != null) {
            finishCapture(InputConstants.Type.MOUSE.getOrCreate(event.button()).getName());
            return true;
        }

        if (!this.isOverFocused(event.x(), event.y())) {
            this.clearFocus();
        }

        boolean handled = super.mouseClicked(event, doubleClick);

        if (this.searchToggledByClick) {
            this.searchToggledByClick = false;
            this.focusSearch();
        }

        return handled;
    }

    private boolean isOverFocused(double mouseX, double mouseY) {
        GuiEventListener focused = this.deepestFocused();

        return focused != null && focused.isMouseOver(mouseX, mouseY);
    }

    private @Nullable GuiEventListener deepestFocused() {
        GuiEventListener focused = this.getFocused();

        while (focused instanceof ContainerEventHandler container && container.getFocused() != null) {
            focused = container.getFocused();
        }

        return focused;
    }

    private boolean isEditingInput() {
        if (this.searchBox != null && this.searchBox.isFocused()) {
            return true;
        }

        for (SettingBox box : this.inputFields) {
            if (box.isFocused()) {
                return true;
            }
        }

        return false;
    }


    private static void commitLocal(@NotNull ISettingsHolder holder) {
        holder.onSettingsChanged();
        SettingsAudit.localChanged(holder);
        SettingsManager.getSettingsManager().markDirty(holder);
    }

    private static @NotNull Component keyDisplay(@NotNull KeySetting setting) {
        if (setting.isUnbound()) return Component.translatable("menu.trc-core.unbound");

        try {
            return InputConstants.getKey(setting.value).getDisplayName();
        } catch (RuntimeException e) {
            return Component.literal(setting.value);
        }
    }

    private void finishCapture(@NotNull String keyName) {
        KeyCapture active = this.capture;

        if (active == null) return;

        this.capture = null;
        active.setting().value = keyName;
        active.button().setMessage(keyText(active.setting()));
        active.sink().commit().accept(active.setting());
        this.refreshValues();
    }

    @Contract(pure = true)
    private static boolean hasChoice(@NotNull ChoiceSetting setting) {
        return setting.values != null
                && !setting.values.isEmpty()
                && setting.index >= 0
                && setting.index < setting.values.size();
    }

    private static @NotNull MutableComponent choiceValue(@NotNull ChoiceSetting setting) {
        String value = setting.values.get(setting.index);

        return setting == TRCCoreClientSettings.CHAT_NOTICES
                ? Component.translatable("menu.trc-core.chat_notices." + value.replace(' ', '_'))
                : Component.literal(value);
    }

    @Contract(pure = true)
    private @NotNull Component getBoolButtonText(@NotNull BoolSetting setting) {
        Component state = setting.value
                ? Component.translatable("menu.trc-core.on").withStyle(ChatFormatting.GREEN)
                : Component.translatable("menu.trc-core.off").withStyle(ChatFormatting.RED);

        return label(setting).append(": ").append(state);
    }

    @Contract(pure = true)
    private @NotNull Component getChoiceButtonText(@NotNull ChoiceSetting setting) {
        MutableComponent currentChoice = hasChoice(setting)
                ? choiceValue(setting)
                : Component.translatable("menu.trc-core.none");

        return label(setting).append(": ").append(currentChoice.withStyle(ChatFormatting.YELLOW));
    }

    private @NotNull Component keyText(@NotNull KeySetting setting) {
        return label(setting).append(": ")
                .append(keyDisplay(setting).copy().withStyle(
                        setting.isUnbound() ? ChatFormatting.GRAY : ChatFormatting.YELLOW));
    }

    private static class SettingBox extends EditBox {
        private final Runnable onCommit;

        private final boolean numeric;

        private boolean dirty;
        private boolean applying;

        SettingBox(net.minecraft.client.gui.Font font, int width, int height, Component message,
                   Runnable onCommit, boolean numeric) {
            super(font, width, height, message);
            this.onCommit = onCommit;
            this.numeric = numeric;
        }

        @Override
        public void insertText(final @NonNull String input) {
            if (!this.numeric) {
                super.insertText(input);
                return;
            }

            StringBuilder digits = new StringBuilder();

            for (int i = 0; i < input.length(); i++) {
                char c = input.charAt(i);

                if (c >= '0' && c <= '9') digits.append(c);
                else if (c == '-' && digits.isEmpty() && this.getCursorPosition() == 0) digits.append(c);
            }

            if (!digits.isEmpty()) super.insertText(digits.toString());
        }

        void markDirty() {
            this.dirty = true;
        }

        void setValueQuietly(String text) {
            if (this.getValue().equals(text)) return;

            this.applying = true;
            try {
                this.setValue(text);
            } finally {
                this.applying = false;
            }
        }

        boolean isApplying() {
            return this.applying;
        }

        void commitIfDirty() {
            if (!this.dirty) return;

            this.dirty = false;
            this.onCommit.run();
        }

        @Override
        public void setFocused(final boolean focused) {
            super.setFocused(focused);

            if (!focused) this.commitIfDirty();
        }

        @Override
        public boolean keyPressed(final @NotNull KeyEvent event) {
            if (event.isConfirmation()) {
                this.commitIfDirty();
                return true;
            }

            return super.keyPressed(event);
        }
    }

    private static class IntSlider extends AbstractSliderButton {
        private final IntSetting setting;

        private final @Nullable String serverModId;

        private final Runnable onDrag;
        private final Runnable onCommit;
        private final int step;

        private boolean held;

        private boolean moved;

        IntSlider(int x, int y, int width, int height, IntSetting setting, @Nullable String serverModId,
                  Runnable onDrag, Runnable onCommit) {
            super(x, y, width, height, Component.empty(), toFraction(setting));
            this.setting = setting;
            this.serverModId = serverModId;
            this.onDrag = onDrag;
            this.onCommit = onCommit;
            this.step = Math.max(1, setting.milliStep / 1000);
            this.updateMessage();
        }

        private static double toFraction(@NotNull IntSetting s) {
            int range = s.maxValue - s.minValue;
            return range <= 0 ? 0.0 : (double) (s.value - s.minValue) / range;
        }

        boolean isHeld() {
            return this.held;
        }

        void refresh() {
            this.value = toFraction(this.setting);
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            MutableComponent value = TRCCoreSettings.isPastTop(this.serverModId, setting)
                    ? Component.translatable("menu.trc-core.value.nobody")
                    : Component.literal(String.valueOf(setting.value));

            this.setMessage(label(setting).append(": ").append(value.withStyle(ChatFormatting.YELLOW)));
        }

        @Override
        protected void applyValue() {
            int range = setting.maxValue - setting.minValue;
            int raw = setting.minValue + (int) Math.round(this.value * range);
            int snapped = setting.minValue + Math.round((float) (raw - setting.minValue) / step) * step;

            int before = setting.value;
            setting.value = Math.clamp(snapped, setting.minValue, setting.maxValue);

            if (setting.value == before) return;

            this.moved = true;
            onDrag.run();
        }

        @Override
        public void onClick(final @NonNull MouseButtonEvent event, final boolean doubleClick) {
            this.held = true;
            this.moved = false;
            super.onClick(event, doubleClick);
        }

        @Override
        public boolean keyPressed(final @NotNull KeyEvent event) {
            int before = this.setting.value;

            if (!super.keyPressed(event)) return false;

            if (this.setting.value != before) onCommit.run();

            return true;
        }

        @Override
        public void onRelease(final @NotNull MouseButtonEvent event) {
            this.held = false;
            super.onRelease(event);

            if (this.moved) onCommit.run();
        }
    }
}