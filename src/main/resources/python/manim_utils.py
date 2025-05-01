# manim_utils.py
from enum import Enum
import itertools
import requests
import hashlib
import os
from typing import Any, Self
from contextlib import contextmanager

import manimpango
from manim import Mobject, Text, Scene, VGroup, MathTex, Axes, DOWN, RIGHT, RED, GREEN, BLUE, YELLOW, PURPLE, ORANGE, TEAL, PINK, WHITE, BLACK
from manim import config
from manim.constants import *
from manim.typing import Point3D
from moviepy import AudioFileClip

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

    def place(self, mobject: Mobject, aligned_edge: Point3D = ORIGIN, buff: float = MED_SMALL_BUFF) -> Mobject:
        """
        Place mobject in the specified region.

        This method ensures the mobject will be placed entirely inside the rectangle.
        In terms of oversize, the mobject will be rescaled uniformly to fit in.
        """
        width = mobject.get_width()
        height = mobject.get_height()
        if width <= 0.0 or height <= 0.0:
            return mobject

        x_min, x_max, y_min, y_max = self.x_min, self.x_max, self.y_min, self.y_max
        short_length = min(x_max - x_min, y_max - y_min)
        while 2.0 * buff > short_length:
            buff /= 2.0
        scale_factor = min((x_max - x_min - 2.0 * buff) / width, (y_max - y_min - 2.0 * buff) / height)
        if scale_factor < 1.0:
            mobject.scale(scale_factor)

        center = 0.5 * ((x_min + x_max) * RIGHT + (y_min + y_max) * UP)
        radius = 0.5 * ((x_max - x_min) * RIGHT + (y_max - y_min) * UP)
        point = center + aligned_edge * (radius - buff)
        return mobject.move_to(point, aligned_edge=aligned_edge)


class LayoutAtom:
    def _resolve_size(self, region: LayoutRegion) -> Any:
        return region


class Layout:
    def __init__(self, layout_direction: LayoutDirection, layout: dict[str, tuple[float, LayoutAtom | Self]]) -> None:
        self._layout_direction: LayoutDirection = layout_direction
        self._layout: dict[str, tuple[float, LayoutAtom | Self]] = layout

    def _resolve_size(self, region: LayoutRegion) -> Any:
        proportion_sum = sum(proportion for (proportion, _) in self._layout.values())
        spans = itertools.pairwise(itertools.accumulate(
            (proportion / proportion_sum for (proportion, _) in self._layout.values()),
            initial=0.0
        ))
        return {
            name: layout._resolve_size(
                LayoutRegion(
                    x_min=region.x_min + (region.x_max - region.x_min) * span_start,
                    x_max=region.x_min + (region.x_max - region.x_min) * span_end,
                    y_min=region.y_min,
                    y_max=region.y_max,
                )
                if self._layout_direction == LayoutDirection.HORIZONTAL
                else LayoutRegion(
                    x_min=region.x_min,
                    x_max=region.x_max,
                    y_min=region.y_max - (region.y_max - region.y_min) * span_end,
                    y_max=region.y_max - (region.y_max - region.y_min) * span_start,
                )
            )
            for (span_start, span_end), (name, (_, layout)) in zip(spans, self._layout.items())
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
        self.background = background.lower()  # "black" or "white"
        
        # Define color palettes for different backgrounds
        self.black_bg_palette = palette or [RED, GREEN, BLUE, YELLOW, PURPLE, ORANGE, TEAL, PINK]
        self.white_bg_palette = palette or ["#D32F2F", "#388E3C", "#1976D2", "#F57F17", "#7B1FA2", "#E64A19", "#00796B", "#C2185B"]
        
        # Text colors based on background
        self.text_color = WHITE if self.background == "black" else BLACK
        self.math_color = WHITE if self.background == "black" else BLACK
        
        # Select appropriate palette based on background
        self.palette = self.black_bg_palette if self.background == "black" else self.white_bg_palette
        
        self._color_index = 0
        # 内部 container，用来统一排列、缩放、对齐
        self.container = VGroup()
        self.container.to_edge(UP)
        self._registered = {}  # mobject -> metadata
        
        # Track whether we've adjusted the camera already
        self._camera_adjusted = False
 
    def clear(self):
        self._color_index = 0
        self.container = VGroup()
        self._registered = {}

        self._camera_adjusted = False
 
    def register(self, mobject, important=False, color=None, opacity=None, adjust_camera=False):
        """
        Register a mobject with the layout manager.
        
        Parameters:
            mobject: The mobject to register
            important: Whether the mobject is important (affects text weight)
            color: Optional color override
            opacity: Optional fill_opacity value
            adjust_camera: Whether to adjust the camera for this mobject
        """
        if isinstance(mobject, VGroup):
            use_color = color
            if use_color is not None:
                try:
                    mobject.set_color(use_color)
                except:
                    pass
                    
            if opacity is not None:
                try:
                    mobject.set_fill(opacity=opacity)
                except:
                    pass
                    
            self._registered[mobject] = {"important": important, "color": use_color, "opacity": opacity}
            
            for sub in mobject:
                self.update_color(sub)
                if opacity is not None:
                    try:
                        sub.set_fill(opacity=opacity)
                    except:
                        pass
                
            if adjust_camera:
                self.gentle_camera_adjustment()
            return
        
        # Only apply color, opacity and weight modifications
        self.update_color_and_weight(mobject, important, color, opacity)
        
        # Only adjust camera if explicitly requested
        if adjust_camera:
            self.gentle_camera_adjustment()
 
    def update_color(self, sub):
        if isinstance(sub, Text):
            use_color = self.text_color
        elif isinstance(sub, (MathTex, Axes)):
            use_color = self.math_color
        else:
            use_color = self.palette[self._color_index % len(self.palette)]
            self._color_index += 1
        try:
            sub.set_color(use_color)
        except Exception:
            pass
 
    def update_color_and_weight(self, mobject, important, color, opacity=None):
        if color is None:
            if isinstance(mobject, Text):
                use_color = self.text_color
            elif isinstance(mobject, (MathTex, Axes)):
                use_color = self.math_color
            else:
                use_color = self.palette[self._color_index % len(self.palette)]
                self._color_index += 1
        else:
            use_color = color
            
        # Store metadata
        self._registered[mobject] = {"important": important, "color": use_color, "opacity": opacity}
        
        # Apply color
        try:
            mobject.set_color(use_color)
        except Exception:
            pass
            
        # Apply opacity if provided
        if opacity is not None:
            try:
                mobject.set_fill(opacity=opacity)
            except Exception:
                pass
            
        # Bold for important Text
        if important and isinstance(mobject, Text):
            try:
                mobject.set_weight("bold")
            except Exception:
                pass
 
    def set_background(self, background):
        """
        Update the background color setting.
        
        Parameters:
            background: "black" or "white"
        """
        self.background = background.lower()
        self.text_color = WHITE if self.background == "black" else BLACK
        self.math_color = WHITE if self.background == "black" else BLACK
        self.palette = self.black_bg_palette if self.background == "black" else self.white_bg_palette
        
        # Update colors for all registered objects
        for mobject, metadata in self._registered.items():
            custom_color = metadata.get("color")
            important = metadata.get("important", False)
            opacity = metadata.get("opacity")
            self.update_color_and_weight(mobject, important, custom_color, opacity)
 
    def gentle_camera_adjustment(self):
        dir_use = self.direction
        if dir_use is None:
            dir_use = DOWN if self.container.height > self.container.width else RIGHT
        self.container.arrange(dir_use, buff=self.buff)
        target_h = self.container.height * 1.1
        # 直接改数值（World units）
        self.scene.camera.frame_height = target_h
        # 保持宽高比
        aspect = self.scene.camera.pixel_width / self.scene.camera.pixel_height
        self.scene.camera.frame_width = target_h * aspect
        self.container.to_edge(UP)
            
    def arrange_objects(self, objects=None, direction=None, buff=None):
        """
        Arrange specified objects (or all registered objects) in the given direction.
        
        Parameters:
            objects: List of objects to arrange (defaults to all registered objects)
            direction: Direction to arrange in (defaults to self.direction)
            buff: Buffer space between objects (defaults to self.buff)
        """
        if objects is None:
            objects = list(self._registered.keys())
            
        if direction is None:
            direction = self.direction
            
        if buff is None:
            buff = self.buff
            
        if not objects:
            return
            
        # Create a temporary group and arrange it
        temp_group = VGroup(*objects)
        temp_group.arrange(direction, buff=buff)


class Title(Text):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.set_weight("bold")
