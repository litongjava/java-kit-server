# manim_toolkit.py
import hashlib
import itertools
import os
import platform
from contextlib import contextmanager
from enum import Enum
from typing import Any, Self

import manimpango
import numpy as np
import requests
from PIL.GimpGradientFile import EPSILON
from manim import Mobject, Text, Scene, Group, Axes, \
    FadeIn, FadeOut, TexTemplate  # DOWN, RIGHT, etc. are imported from manim.constants
from manim import config
from manim.constants import *  # Imports RIGHT, UP, ORIGIN, MED_SMALL_BUFF, EPSILON, etc.
from manim.typing import Point3D
from moviepy import AudioFileClip

# --- Font Detection ---
DEFAULT_FONT = "PingFang SC"
FALLBACK_FONTS = ["Microsoft YaHei", "Songti SC", "Arial Unicode MS"]
# --- TTS Caching Setup ---
AUDIO_DIR = os.path.join(config.media_dir, "audio")
os.makedirs(AUDIO_DIR, exist_ok=True)

SCRIPT_DIR = os.path.join(config.media_dir, "script")
os.makedirs(SCRIPT_DIR, exist_ok=True)


def get_cjk_font_name():
    system = platform.system()
    if system == "Windows":
        return "SimSun"  # 宋体
    elif system == "Darwin":  # macOS
        return "Songti SC"
    elif system == "Linux":
        return "Noto Sans CJK SC"
    else:
        return "Arial Unicode MS"


def get_available_font():
    """Returns a font name if available, else None."""
    available_fonts = manimpango.list_fonts()
    if DEFAULT_FONT in available_fonts:
        return DEFAULT_FONT
    for font in FALLBACK_FONTS:
        if font in available_fonts:
            return font
    return None


def get_cjk_template(font_name):
    cjk_template = TexTemplate(
        tex_compiler="xelatex",
        output_format=".xdv",
        preamble=rf"""
\usepackage{{amsmath}}
\usepackage{{amssymb}}
\usepackage{{fontspec}} 
\usepackage{{xeCJK}}
\setCJKmainfont{{{font_name}}}
\setCJKsansfont{{{font_name}}} 
\setCJKmonofont{{{font_name}}}
"""
    )
    return cjk_template


def update_scene_number(scene: Scene, number_str: str):
    """在右上角淡入场景编号，自动淡出上一个编号。"""
    if not hasattr(scene, "_scene_num_mob"):
        scene._scene_num_mob = None
    new_scene_num = Text(number_str).to_corner(UR, buff=MED_LARGE_BUFF).set_z_index(10)
    animations = [FadeIn(new_scene_num, run_time=0.5)]
    if scene._scene_num_mob is not None:
        animations.append(FadeOut(scene._scene_num_mob, run_time=0.5))
    scene.play(*animations)
    scene._scene_num_mob = new_scene_num


class CustomVoiceoverTracker:
    """Tracks audio path and duration for TTS."""

    def __init__(self, audio_path, duration):
        self.audio_path = audio_path
        self.duration = duration


def get_cache_filename(text: str) -> str:
    """Generates a unique filename based on the MD5 hash of the text."""
    text_hash = hashlib.md5(text.encode('utf-8')).hexdigest()
    return os.path.join(AUDIO_DIR, f"{text_hash}.mp3")


@contextmanager
def custom_voiceover_tts(text: str,
                         token: str = "123456",
                         base_url: str = "http://13.216.69.13/api/manim/tts"):
    script_path = os.path.join(SCRIPT_DIR, "script.txt")
    try:
        with open(script_path, "a", encoding="utf-8") as log_file:
            log_file.write(text + "\n")
    except Exception:
        # If logging fails, continue without interrupting TTS
        pass
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

        self._scaled_factor: float = 1.0
        self._placed_mobjects: list[Mobject] = []
        self._placed_axes_origin: Point3D = None  # 新增：存储放置后的坐标系原点

    @property
    def width(self) -> float:
        return self.x_max - self.x_min

    @property
    def height(self) -> float:
        return self.y_max - self.y_min

    def get_center(self) -> Point3D:
        return np.array([(self.x_min + self.x_max) / 2, (self.y_min + self.y_max) / 2, 0])

    def get_axes_origin(self) -> Point3D:
        """获取被放置的坐标系的原点位置"""
        if self._placed_axes_origin is not None:
            return self._placed_axes_origin
        else:
            return self.get_center()  # 如果没有坐标系，返回区域中心

    def place(self, mobject: Mobject, aligned_edge: Point3D = ORIGIN, buff: float = MED_SMALL_BUFF) -> Mobject:
        """
        Place mobject in the specified region.

        This method ensures the mobject will be placed entirely inside the rectangle.
        In terms of oversize, the mobject will be rescaled uniformly to fit in.
        """
        Group(*self._placed_mobjects).scale(1.0 / self._scaled_factor)  # recover
        if mobject not in self._placed_mobjects:
            self._placed_mobjects.append(mobject)
        group = Group(*self._placed_mobjects).arrange(DOWN)

        mobject_w = group.get_width()
        mobject_h = group.get_height()

        if mobject_w <= EPSILON or mobject_h <= EPSILON:
            group.move_to(self.get_center())
            # 如果是坐标系，记录其原点位置
            self._track_axes_origin(group)
            return mobject

        region_w = self.width
        region_h = self.height

        if region_w <= EPSILON or region_h <= EPSILON:
            group.move_to(self.get_center())
            self._track_axes_origin(group)
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

        self._scaled_factor = scale_factor
        if scale_factor < 1.0 - EPSILON:
            group.scale(scale_factor)

        center_coords = self.get_center()

        eff_half_width = max(0, region_w / 2.0 - current_buff)
        eff_half_height = max(0, region_h / 2.0 - current_buff)

        target_point_coords = center_coords.copy()
        target_point_coords[0] += aligned_edge[0] * eff_half_width
        target_point_coords[1] += aligned_edge[1] * eff_half_height

        group.move_to(target_point_coords, aligned_edge=aligned_edge)

        # 如果是坐标系，记录其原点位置
        self._track_axes_origin(group)

        return mobject

    def _track_axes_origin(self, group: Group):
        """检查group中是否包含Axes对象，如果有则记录其原点位置"""
        for mobject in group:
            if isinstance(mobject, Axes):
                self._placed_axes_origin = mobject.get_origin()
                break
            elif hasattr(mobject, 'submobjects'):
                # 递归检查submobjects
                for submob in mobject.submobjects:
                    if isinstance(submob, Axes):
                        self._placed_axes_origin = submob.get_origin()
                        return


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


class Title(Text):
    def __init__(self, *args, **kwargs):
        if 'font_weight' in kwargs and 'weight' not in kwargs:
            kwargs['weight'] = kwargs.pop('font_weight')

        if 'weight' not in kwargs:
            kwargs['weight'] = BOLD

        super().__init__(*args, **kwargs)