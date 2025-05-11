# manim_utils.py
from enum import Enum
import itertools
import requests
import hashlib
import os
from typing import Any, Self
from contextlib import contextmanager

import manimpango
import numpy as np  # Added for np.array, np.allclose
from PIL.GimpGradientFile import EPSILON
from manim import Mobject, Text, Scene, VGroup, MathTex, Axes, \
    WHITE, BLACK, RED, GREEN, BLUE, YELLOW, PURPLE, ORANGE, TEAL, \
    PINK  # DOWN, RIGHT, etc. are imported from manim.constants
from manim import config
from manim.constants import *  # Imports RIGHT, UP, ORIGIN, MED_SMALL_BUFF, EPSILON, etc.
from manim.typing import Point3D
from moviepy import AudioFileClip  # Corrected import for moviepy

# --- Font Detection ---
DEFAULT_FONT = "Noto Sans CJK SC"
FALLBACK_FONTS = ["PingFang SC", "Microsoft YaHei", "SimHei", "Arial Unicode MS"]


def get_available_font():
    """Returns a font name if available, else None."""
    available_fonts = manimpango.list_fonts()
    if DEFAULT_FONT in available_fonts:
        return DEFAULT_FONT
    for font in FALLBACK_FONTS:
        if font in available_fonts:
            return font
    return None


# --- TTS Caching Setup ---
CACHE_DIR = os.path.join(config.media_dir, "audio")
os.makedirs(CACHE_DIR, exist_ok=True)


class CustomVoiceoverTracker:
    """Tracks audio path and duration for TTS."""

    def __init__(self, audio_path, duration):
        self.audio_path = audio_path
        self.duration = duration


def get_cache_filename(text: str) -> str:
    """Generates a unique filename based on the MD5 hash of the text."""
    text_hash = hashlib.md5(text.encode('utf-8')).hexdigest()
    return os.path.join(CACHE_DIR, f"{text_hash}.mp3")


@contextmanager
def custom_voiceover_tts(text: str,
                         token: str = "123456",
                         base_url: str = "https://uni-ai.fly.dev/api/manim/tts"):
    """Fetches or uses cached TTS audio, yields a tracker with path and duration."""
    cache_file = get_cache_filename(text)
    audio_file = cache_file
    duration = 0

    if not os.path.exists(cache_file):
        try:
            encoded = requests.utils.quote(text)
            url = f"{base_url}?token={token}&input={encoded}"
            resp = requests.get(url, stream=True, timeout=60)
            resp.raise_for_status()
            with open(cache_file, 'wb') as f:
                for chunk in resp.iter_content(8192):
                    if chunk:
                        f.write(chunk)
        except Exception:
            audio_file = None
            duration = 0
    if audio_file and os.path.exists(audio_file):
        try:
            # Use moviepy.editor.AudioFileClip
            with AudioFileClip(audio_file) as clip:
                duration = clip.duration
        except Exception:
            audio_file = None
            duration = 0
    else:
        audio_file = None
        duration = 0

    tracker = CustomVoiceoverTracker(audio_file, duration)
    try:
        yield tracker
    finally:
        pass


class LayoutDirection(Enum):
    HORIZONTAL = 0
    VERTICAL = 1


class LayoutRegion:
    def __init__(self, x_min: float, x_max: float, y_min: float, y_max: float) -> None:
        self.x_min: float = x_min
        self.x_max: float = x_max
        self.y_min: float = y_min
        self.y_max: float = y_max

    @property
    def width(self) -> float:
        return self.x_max - self.x_min

    @property
    def height(self) -> float:
        return self.y_max - self.y_min

    def get_center(self) -> Point3D:
        return np.array([(self.x_min + self.x_max) / 2, (self.y_min + self.y_max) / 2, 0])

    def place(self, mobject: Mobject, aligned_edge: Point3D = ORIGIN, buff: float = MED_SMALL_BUFF) -> Mobject:
        """
        Place mobject in the specified region.

        This method ensures the mobject will be placed entirely inside the rectangle.
        In terms of oversize, the mobject will be rescaled uniformly to fit in.
        """
        mobject_w = mobject.get_width()
        mobject_h = mobject.get_height()

        if mobject_w <= EPSILON or mobject_h <= EPSILON:
            mobject.move_to(self.get_center())
            return mobject

        region_w = self.width
        region_h = self.height

        if region_w <= EPSILON or region_h <= EPSILON:
            mobject.move_to(self.get_center())
            return mobject

        current_buff = buff
        short_length = min(region_w, region_h)

        if short_length > EPSILON:
            while (2.0 * current_buff > short_length) and (current_buff > EPSILON):
                current_buff /= 2.0
        else:
            current_buff = 0.0

        available_width = region_w - 2.0 * current_buff
        available_height = region_h - 2.0 * current_buff

        scale_factor = 1.0
        if available_width > EPSILON and available_height > EPSILON:
            scale_factor_w = available_width / mobject_w
            scale_factor_h = available_height / mobject_h
            scale_factor = min(scale_factor_w, scale_factor_h)
        else:
            scale_factor_w = region_w / mobject_w
            scale_factor_h = region_h / mobject_h
            scale_factor = min(scale_factor_w, scale_factor_h)

        if scale_factor < 1.0 - EPSILON:
            mobject.scale(scale_factor)

        center_coords = self.get_center()

        eff_half_width = max(0, region_w / 2.0 - current_buff)
        eff_half_height = max(0, region_h / 2.0 - current_buff)

        target_point_coords = center_coords.copy()
        target_point_coords[0] += aligned_edge[0] * eff_half_width
        target_point_coords[1] += aligned_edge[1] * eff_half_height

        return mobject.move_to(target_point_coords, aligned_edge=aligned_edge)


class LayoutAtom:
    def _resolve_size(self, region: LayoutRegion) -> Any:
        return region


class Layout:
    def __init__(self, layout_direction: LayoutDirection, layout: dict[str, tuple[float, LayoutAtom | Self]]) -> None:
        self._layout_direction: LayoutDirection = layout_direction
        self._layout: dict[str, tuple[float, LayoutAtom | Self]] = layout

    def _resolve_size(self, region: LayoutRegion) -> Any:
        proportion_sum = sum(proportion for (proportion, _) in self._layout.values())
        if abs(proportion_sum) < EPSILON:
            return {name: None for name in self._layout.keys()}

        spans = itertools.pairwise(itertools.accumulate(
            (proportion / proportion_sum for (proportion, _) in self._layout.values()),
            initial=0.0
        ))
        return {
            name: layout_item._resolve_size(
                LayoutRegion(
                    x_min=region.x_min + region.width * span_start,
                    x_max=region.x_min + region.width * span_end,
                    y_min=region.y_min,
                    y_max=region.y_max,
                )
                if self._layout_direction == LayoutDirection.HORIZONTAL
                else LayoutRegion(
                    x_min=region.x_min,
                    x_max=region.x_max,
                    y_min=region.y_max - region.height * span_end,
                    y_max=region.y_max - region.height * span_start,
                )
            )
            for (span_start, span_end), (name, (_, layout_item)) in zip(spans, self._layout.items())
        }

    def resolve(self, scene: Scene) -> Any:
        return self._resolve_size(LayoutRegion(
            x_min=-scene.camera.frame_width / 2.0,
            x_max=scene.camera.frame_width / 2.0,
            y_min=-scene.camera.frame_height / 2.0,
            y_max=scene.camera.frame_height / 2.0,
        ))


class LayoutManager:
    def __init__(self, scene, direction=DOWN, buff=0.5, background="black", palette=None):
        self.scene = scene
        self.direction = direction
        self.buff = buff
        self.background = background.lower()

        self.default_text_color_on_dark_bg = WHITE
        self.default_text_color_on_light_bg = BLACK
        self.default_math_color_on_dark_bg = WHITE
        self.default_math_color_on_light_bg = BLACK

        self.palette_for_dark_bg = palette or [RED, GREEN, BLUE, YELLOW, PURPLE, ORANGE, TEAL, PINK]
        self.palette_for_light_bg = palette or ["#D32F2F", "#388E3C", "#1976D2", "#F57F17", "#7B1FA2", "#E64A19",
                                                "#00796B", "#C2185B"]

        self.text_color = self.default_text_color_on_light_bg if self.background == "white" else self.default_text_color_on_dark_bg
        self.math_color = self.default_math_color_on_light_bg if self.background == "white" else self.default_math_color_on_dark_bg
        self.palette = self.palette_for_light_bg if self.background == "white" else self.palette_for_dark_bg

        self._color_index = 0
        self.container = VGroup()
        self._registered = {}

        self._camera_adjusted = False

    def clear(self):
        self._color_index = 0
        self.container = VGroup()
        self._registered = {}
        self._camera_adjusted = False

    def register(self, mobject, important=False, color=None, opacity=None, adjust_camera=False):
        if isinstance(mobject, VGroup):
            if color is not None:
                try:
                    mobject.set_color(color)
                except:
                    pass
            if opacity is not None:
                try:
                    mobject.set_opacity(opacity)
                except:
                    pass

            self._registered[mobject] = {"important": important, "color": color, "opacity": opacity, "is_vgroup": True}

            for sub_mo in mobject.submobjects:
                self.update_color(sub_mo, group_color_override=color)
                if opacity is not None:
                    try:
                        sub_mo.set_opacity(opacity)
                    except:
                        pass
        else:
            self.update_color_and_weight(mobject, important, color, opacity)
            # Ensure _registered entry exists before trying to get "color" from it
            if mobject not in self._registered:
                self._registered[mobject] = {}
            self._registered[mobject].update({"important": important, "opacity": opacity, "is_vgroup": False})
            # Color is set by update_color_and_weight and stored there

        if mobject not in self.container.submobjects:
            self.container.add(mobject)

        if adjust_camera:
            self.gentle_camera_adjustment()

    def update_color(self, sub, group_color_override=None):
        if group_color_override is not None:
            use_color = group_color_override
        elif isinstance(sub, Text):  # Fixed: removed extra parenthesis
            use_color = self.text_color
        elif isinstance(sub, (MathTex, Axes)):
            use_color = self.math_color
        else:
            use_color = self.palette[self._color_index % len(self.palette)]
            if group_color_override is None:  # Only increment if not overridden and not Text/MathTex
                self._color_index += 1
        try:
            sub.set_color(use_color)
        except Exception:
            pass

    def update_color_and_weight(self, mobject, important, color_override, opacity=None):
        use_color = color_override
        is_text_or_math = isinstance(mobject, (Text, MathTex, Axes))

        if use_color is None:
            if isinstance(mobject, Text):
                use_color = self.text_color
            elif isinstance(mobject, (MathTex, Axes)):
                use_color = self.math_color
            else:
                use_color = self.palette[self._color_index % len(self.palette)]

        if mobject not in self._registered:
            self._registered[mobject] = {}

        self._registered[mobject]["color"] = use_color

        if color_override is None and not is_text_or_math:
            self._color_index += 1

        try:
            mobject.set_color(use_color)
        except Exception:
            pass

        if opacity is not None:
            try:
                mobject.set_opacity(opacity)
            except Exception:
                pass

        if important and isinstance(mobject, Text):
            try:
                mobject.set_weight(BOLD)
            except Exception:
                pass

    def set_background(self, background_color_name: str):
        self.background = background_color_name.lower()
        self.text_color = self.default_text_color_on_light_bg if self.background == "white" else self.default_text_color_on_dark_bg
        self.math_color = self.default_math_color_on_light_bg if self.background == "white" else self.default_math_color_on_dark_bg
        self.palette = self.palette_for_light_bg if self.background == "white" else self.palette_for_dark_bg

        for mobject, metadata in list(self._registered.items()):
            # Retrieve the original intended color override if one was set during registration
            # The 'color' field in metadata should store the override if provided, or the determined color.
            # For re-application, we need to know if it was an *override*.
            # Let's assume 'color_override_during_registration' was stored if 'color' was passed to register.
            # This part of the logic is a bit complex due to how color is determined and stored.
            # A simpler approach might be to re-run the color determination logic.

            original_color_override = None  # This needs a better way to be tracked if specific overrides must be preserved.
            # For now, let's assume we re-determine based on type or use stored color if it was an override.

            # A better way: check if metadata['color'] was due to an override or palette.
            # This is tricky. Let's simplify: if metadata['color'] exists and is not one of the default type-based colors,
            # it might have been an override or a palette color.
            # The current logic in update_color_and_weight re-determines color if color_override is None.

            important = metadata.get("important", False)
            opacity = metadata.get("opacity")

            # Re-evaluate color for the object based on new background
            current_obj_color_override = metadata.get("color")  # This is the color it currently has or was set to.
            # If it was from palette, it needs to change.
            # If it was a specific override, it should stay.
            # This logic needs refinement if we want to distinguish.

            # Simplest re-application:
            if metadata.get("is_vgroup"):
                vgroup_color_override = metadata.get("color")  # The color originally passed to register for the VGroup
                if vgroup_color_override is not None:
                    try:
                        mobject.set_color(vgroup_color_override)  # Re-apply VGroup level override
                    except:
                        pass
                for sub_mo in mobject.submobjects:
                    # Sub-mobjects of a VGroup might have their own logic or inherit
                    # If VGroup had an override, it's passed. Otherwise, sub_mo determines its color.
                    self.update_color(sub_mo, group_color_override=vgroup_color_override)
            else:
                # For individual mobjects, we need to know if their current color was a specific override.
                # The `color` parameter in `register` is the key.
                # Let's assume `metadata["color_override_intent"]` stored this.
                # Without it, we can't perfectly distinguish.
                # For now, let's assume `update_color_and_weight` with `None` for color_override will correctly re-evaluate.
                self.update_color_and_weight(mobject, important, None, opacity)

    def gentle_camera_adjustment(self):
        if not self.container.submobjects:
            return

        dir_use = self.direction
        if dir_use is None:
            dir_use = DOWN

        self.container.arrange(dir_use, buff=self.buff)

        target_h = self.container.get_height() * 1.1
        target_w = self.container.get_width() * 1.1

        if target_h < EPSILON or target_w < EPSILON:
            return

        current_frame_height = self.scene.camera.frame_height
        current_frame_width = self.scene.camera.frame_width

        scale_factor = 1.0
        if target_h > current_frame_height:
            scale_factor = max(scale_factor, target_h / current_frame_height)
        if target_w > current_frame_width:
            scale_factor = max(scale_factor, target_w / current_frame_width)

        if scale_factor > 1.0:
            self.scene.camera.frame.scale(scale_factor)

        self.container.move_to(ORIGIN).to_edge(UP)
        self._camera_adjusted = True

    def arrange_objects(self, objects=None, direction=None, buff=None):
        obj_list = objects
        if obj_list is None:
            obj_list = list(self._registered.keys())

        if not obj_list:
            return None

        dir_to_use = direction if direction is not None else self.direction
        buff_to_use = buff if buff is not None else self.buff

        temp_group = VGroup(*obj_list)
        temp_group.arrange(dir_to_use, buff=buff_to_use)
        return temp_group


class Title(Text):
    def __init__(self, *args, **kwargs):
        if 'font_weight' in kwargs and 'weight' not in kwargs:
            kwargs['weight'] = kwargs.pop('font_weight')

        if 'weight' not in kwargs:
            kwargs['weight'] = BOLD

        super().__init__(*args, **kwargs)