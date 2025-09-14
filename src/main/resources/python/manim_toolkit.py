# manim_toolkit.py
from __future__ import annotations

import hashlib
import itertools
import logging
import os
import platform
import re
import sys
import time
from contextlib import contextmanager
from enum import Enum
from pathlib import Path
from typing import Any, Self
from typing import Optional

import manimpango
import matplotlib.pyplot as plt
import requests
from PIL.GimpGradientFile import EPSILON
from manim import *
from manim import ImageMobject
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
                         base_url: str = "http://127.0.0.1/api/manim/tts"):
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


def make_circle_on_axes(axes: Axes, R: float, **kwargs) -> VMobject:
    """
    在给定的 Axes 上绘制一个半径为 R 的圆，圆心在原点 (0,0)。

    参数
    ----
    axes : Axes
        Manim 的坐标系对象
    R : float
        圆的半径，使用数据坐标
    **kwargs :
        传递给曲线的其他绘制参数，例如 color, stroke_width 等

    返回
    ----
    VMobject
        表示该圆的曲线对象
    """
    return axes.plot_parametric_curve(
        lambda t: np.array([R * np.cos(t), R * np.sin(t), 0]),
        t_range=[0, TAU],
        **kwargs
    )


# ----------------------------
# 轻量版 MatplotlibFigure（把 Matplotlib Figure 转成 ImageMobject）
# ----------------------------
def _fig_to_rgba_array(fig, dpi=220, transparent=True):
    fig.set_dpi(dpi)
    if transparent:
        fig.patch.set_alpha(0)
        for ax in fig.axes:
            try:
                ax.set_facecolor("none")
            except Exception:
                pass
    fig.canvas.draw()
    w, h = fig.canvas.get_width_height()
    buf = np.frombuffer(fig.canvas.tostring_argb(), dtype=np.uint8).reshape(h, w, 4)
    rgba = buf[:, :, [1, 2, 3, 0]]  # ARGB -> RGBA
    return rgba


class MatplotlibFigure(ImageMobject):
    def __init__(self, fig, size=None, dpi=220, transparent=True, close_figure=True, **kwargs):
        arr = _fig_to_rgba_array(fig, dpi=dpi, transparent=transparent)
        super().__init__(arr, **kwargs)
        if size is not None:
            if isinstance(size, (tuple, list)) and len(size) == 2:
                self.set(width=float(size[0]), height=float(size[1]))
            else:
                self.set(width=float(size))
        if close_figure:
            plt.close(fig)


"""
抗造版 smart_image：
- 并发安全：文件级锁 + 原子落盘（.part -> rename）
- 重试机制：指数退避 + 可配置
- 可观测性：标准 logging，环境变量可控
- 行为可控：强制刷新、超时、重试次数均可配

用法（示例见文末）：
    from smart_image import smart_image
    img = smart_image("牛顿.png")  # 不存在则调用生成接口并下载；存在则直接用
"""

# -------------------------
# 可配置项（也支持通过环境变量覆盖）
# -------------------------
API_HOST = os.getenv("IMG_API_HOST", "https://api.image.explanation.fun")
API_TOKEN = os.getenv("IMG_API_TOKEN", "123456")
API_PATH = os.getenv("IMG_API_PATH", "/api/image/generate")
API_TIMEOUT = float(os.getenv("IMG_API_TIMEOUT", "60"))  # 生成接口超时（秒）
DL_TIMEOUT = float(os.getenv("IMG_DL_TIMEOUT", "60"))  # 下载超时（秒）
RETRIES = int(os.getenv("IMG_RETRIES", "3"))  # 重试次数（不含首次）
BACKOFF = float(os.getenv("IMG_BACKOFF", "1.6"))  # 退避倍率
FORCE_REFRESH = os.getenv("IMG_FORCE_REFRESH", "0") == "1"  # 1=忽略本地缓存强制刷新
LOCK_TIMEOUT = float(os.getenv("IMG_LOCK_TIMEOUT", "300"))  # 获取锁的最长等待秒数
LOG_LEVEL = os.getenv("IMG_LOG_LEVEL", "INFO").upper()  # DEBUG/INFO/WARN/ERROR

# -------------------------
# 日志
# -------------------------
logger = logging.getLogger("smart_image")
if not logger.handlers:
    handler = logging.StreamHandler(sys.stdout)
    fmt = logging.Formatter("[%(asctime)s][%(levelname)s][smart_image] %(message)s")
    handler.setFormatter(fmt)
    logger.addHandler(handler)
logger.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))


# -------------------------
# 轻量跨平台文件锁
#   - Linux/macOS: fcntl
#   - Windows: msvcrt
# -------------------------
class FileLock:
    def __init__(self, path: Path):
        self.path = Path(path)
        self._fh = None

    def acquire(self, timeout: float = LOCK_TIMEOUT, poll_interval: float = 0.2):
        start = time.time()
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self._fh = open(self.path, "a+b")
        while True:
            try:
                if platform.system() == "Windows":
                    import msvcrt
                    msvcrt.locking(self._fh.fileno(), msvcrt.LK_NBLCK, 1)
                else:
                    import fcntl
                    fcntl.flock(self._fh.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
                # 锁定成功
                return
            except Exception:
                # 锁被占用
                if time.time() - start > timeout:
                    raise TimeoutError(f"Acquire lock timeout: {self.path}")
                time.sleep(poll_interval)

    def release(self):
        if not self._fh:
            return
        try:
            if platform.system() == "Windows":
                import msvcrt
                self._fh.seek(0)
                msvcrt.locking(self._fh.fileno(), msvcrt.LK_UNLCK, 1)
            else:
                import fcntl
                fcntl.flock(self._fh.fileno(), fcntl.LOCK_UN)
        finally:
            try:
                self._fh.close()
            except Exception:
                pass
            self._fh = None

    def __enter__(self):
        self.acquire()
        return self

    def __exit__(self, exc_type, exc, tb):
        self.release()


# -------------------------
# HTTP（用 requests 但延迟导入，避免未使用时报错）
# -------------------------
def _requests():
    try:
        import requests
        return requests
    except Exception as e:
        raise RuntimeError(
            "This module requires 'requests'. Please install it: pip install requests"
        ) from e


# -------------------------
# 工具函数
# -------------------------
def _topic_from_filename(filename: str) -> str:
    stem = Path(filename).stem
    return re.sub(r"[-_]+", " ", stem).strip() or "image"


def _api_generate(topic: str) -> str:
    """调用生成接口，返回图片直链 URL。带重试。"""
    req = _requests()
    url = f"{API_HOST.rstrip('/')}{API_PATH}"
    headers = {
        "Authorization": f"Bearer {API_TOKEN}",
    }
    last_exc = None
    for attempt in range(RETRIES + 1):
        try:
            logger.debug(f"Generate attempt {attempt}: {url} topic={topic!r}")
            r = req.get(url, params={"topic": topic}, headers=headers, timeout=API_TIMEOUT)
            r.raise_for_status()
            data = r.json()
            if not (isinstance(data, dict) and data.get("ok") is True):
                raise RuntimeError(f"Bad response: {data}")
            img_url = data["data"]["url"]
            if not isinstance(img_url, str) or not img_url:
                raise RuntimeError(f"Missing url in response: {data}")
            logger.info(f"Generated image for topic={topic!r}: {img_url}")
            return img_url
        except Exception as e:
            last_exc = e
            if attempt < RETRIES:
                sleep_s = (BACKOFF ** attempt) * 0.8 + 0.2
                logger.warning(f"Generate failed (attempt {attempt}): {e}; retry in {sleep_s:.1f}s")
                time.sleep(sleep_s)
            else:
                break
    raise RuntimeError(f"Generate failed after retries: {last_exc}") from last_exc


def _download(url: str, dest_part: Path):
    """下载到临时 .part 文件（覆盖写），成功后由调用方 rename。带重试。"""
    req = _requests()
    last_exc = None
    for attempt in range(RETRIES + 1):
        try:
            logger.debug(f"Download attempt {attempt}: {url} -> {dest_part}")
            with req.get(url, stream=True, timeout=DL_TIMEOUT) as resp:
                resp.raise_for_status()
                dest_part.parent.mkdir(parents=True, exist_ok=True)
                with open(dest_part, "wb") as f:
                    for chunk in resp.iter_content(chunk_size=1 << 15):
                        if chunk:
                            f.write(chunk)
            logger.info(f"Downloaded -> {dest_part} ({dest_part.stat().st_size} bytes)")
            return
        except Exception as e:
            last_exc = e
            if attempt < RETRIES:
                sleep_s = (BACKOFF ** attempt) * 0.8 + 0.2
                logger.warning(f"Download failed (attempt {attempt}): {e}; retry in {sleep_s:.1f}s")
                time.sleep(sleep_s)
            else:
                break
    raise RuntimeError(f"Download failed after retries: {last_exc}") from last_exc


def _ensure_local_image(
        filename: str, topic: Optional[str] = None
) -> Path:
    """
    并发安全地确保 filename 存在：
    - 若已存在且不强制刷新：直接返回
    - 否则：获取文件锁 -> 调用生成接口 -> 下载到 .part -> 原子 rename -> 释放锁
    """
    p = Path(filename)
    lock_path = p.with_suffix(p.suffix + ".lock")
    part_path = p.with_suffix(p.suffix + ".part")

    # 快速路径：存在且不刷新
    if p.exists() and not FORCE_REFRESH:
        logger.debug(f"Cache hit: {p}")
        return p

    # 锁内再检查一次，避免惊群
    with FileLock(lock_path):
        if p.exists() and not FORCE_REFRESH:
            logger.debug(f"[locked] Cache hit after wait: {p}")
            return p

        top = topic or _topic_from_filename(filename)
        img_url = _api_generate(top)

        # 下载到 .part，成功后 rename 到最终文件名（原子操作）
        _download(img_url, part_path)
        # Windows 下若目标存在需要先删除
        if p.exists():
            try:
                p.unlink()
            except Exception:
                pass
        part_path.replace(p)  # 原子替换
        logger.info(f"Ready: {p}")
        return p


# -------------------------
# 公共入口
# -------------------------
def generate_image(
        filename: str,
        *,
        topic: Optional[str] = None,
        **image_mobject_kwargs,
) -> ImageMobject:
    """
    用主题自动生成/下载图片并返回 ImageMobject。
    - filename：希望最终保存的本地文件名（相对路径则相对 CWD）
    - topic：可选，默认从文件名推导（去后缀，-/_ 转空格）
    - 其余参数原样传给 ImageMobject
    """
    local = _ensure_local_image(filename, topic=topic)
    return ImageMobject(str(local), **image_mobject_kwargs)