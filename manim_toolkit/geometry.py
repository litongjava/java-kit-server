import json
import os

from manim import *


def fig(json_input):
    """
    Create a geometry figure from PGDP JSON output.
    
    Args:
        json_input (str or dict): Either a path to the PGDP output JSON file,
                                  or a dictionary containing the PGDP data
    
    Returns:
        VGroup: Manim VGroup containing all geometric elements
    
    Example:
        # From file
        geometric_figure = fig("pgdp-output.json")
        
        # From dictionary
        geometric_figure = fig(data_dict)
        
        self.add(geometric_figure)
    """

    # Load JSON data
    if isinstance(json_input, dict):
        data = json_input
    elif isinstance(json_input, (str, bytes, os.PathLike)):
        with open(json_input, 'r') as f:
            data = json.load(f)
    else:
        raise TypeError("json_input must be either a file path (str) or a dictionary")

    # Handle nested format where relation_result contains the actual data
    if 'relation_result' in data:
        data = data['relation_result']

    # Configuration
    scale_factor = 0.02
    offset_x = -2.75
    offset_y = 1.5

    def convert_coordinates(point):
        """Convert image coordinates to Manim coordinates"""
        if point is None or not point:
            return np.array([0, 0, 0])
        try:
            x = (point[0] * scale_factor) + offset_x
            y = -(point[1] * scale_factor) + offset_y
            return np.array([x, y, 0])
        except (IndexError, TypeError):
            return np.array([0, 0, 0])

    def line_intersection_point(line1, line2):
        """Calculate intersection point of two lines"""
        try:
            p1 = line1.get_start()[:2]
            p2 = line1.get_end()[:2]
            p3 = line2.get_start()[:2]
            p4 = line2.get_end()[:2]

            d1 = p2 - p1
            d2 = p4 - p3

            denom = d1[0] * d2[1] - d1[1] * d2[0]

            if abs(denom) < 1e-10:
                return None

            t = ((p3[0] - p1[0]) * d2[1] - (p3[1] - p1[1]) * d2[0]) / denom

            intersect_x = p1[0] + t * d1[0]
            intersect_y = p1[1] + t * d1[1]

            return np.array([intersect_x, intersect_y, 0])
        except:
            return None

    def get_line_angle(line, from_center):
        """Get the angle of a line's direction from a given center point"""
        start = line.get_start()
        end = line.get_end()

        dist_to_start = np.linalg.norm(start - from_center)
        dist_to_end = np.linalg.norm(end - from_center)

        if dist_to_start < dist_to_end:
            direction = end - start
        else:
            direction = start - end

        return np.arctan2(direction[1], direction[0])

    def create_right_angle_marker(center, line1, line2, size=0.12):
        """Create properly oriented right angle marker"""
        try:
            start1, end1 = line1.get_start(), line1.get_end()
            start2, end2 = line2.get_start(), line2.get_end()

            if np.linalg.norm(start1 - center) < np.linalg.norm(end1 - center):
                dir1 = (end1 - start1) / (np.linalg.norm(end1 - start1) + 1e-10)
            else:
                dir1 = (start1 - end1) / (np.linalg.norm(start1 - end1) + 1e-10)

            if np.linalg.norm(start2 - center) < np.linalg.norm(end2 - center):
                dir2 = (end2 - start2) / (np.linalg.norm(end2 - start2) + 1e-10)
            else:
                dir2 = (start2 - end2) / (np.linalg.norm(start2 - end2) + 1e-10)

            corner1 = center
            corner2 = center + dir1[:3] * size
            corner3 = center + dir1[:3] * size + dir2[:3] * size
            corner4 = center + dir2[:3] * size

            square = Polygon(corner1, corner2, corner3, corner4, color=BLACK, stroke_width=2)
            return square

        except Exception:
            angle1 = get_line_angle(line1, center)
            square = Square(side_length=size, color=BLACK, stroke_width=2)
            square.rotate(angle1)
            square.move_to(center)
            return square

    def create_angle_arc_marker(center, line1, line2, radius=0.25):
        """Create properly oriented angle arc marker"""
        try:
            angle1 = get_line_angle(line1, center)
            angle2 = get_line_angle(line2, center)

            angle_diff = angle2 - angle1

            while angle_diff > np.pi:
                angle_diff -= 2 * np.pi
            while angle_diff < -np.pi:
                angle_diff += 2 * np.pi

            if abs(angle_diff) > np.pi * 0.9:
                if angle_diff > 0:
                    angle_diff = angle_diff - 2 * np.pi
                else:
                    angle_diff = angle_diff + 2 * np.pi

            arc = Arc(
                radius=radius,
                start_angle=angle1,
                angle=angle_diff,
                color=BLACK,
                stroke_width=2
            )
            arc.move_arc_center_to(center)
            return arc

        except Exception:
            return Arc(radius=radius, angle=PI / 4, color=BLACK, stroke_width=2).move_to(center)

    # Parse data
    points_data = {}
    lines_data = []
    circles_data = []
    all_labels = []
    markers = {'perpendicular': [], 'angle': []}

    # Extract points
    try:
        if 'geos' in data and 'points' in data['geos']:
            for point in data['geos']['points']:
                if point and 'id' in point and 'loc' in point and point['loc']:
                    point_id = point['id']
                    coords = point['loc'][0] if len(point['loc']) > 0 else [0, 0]
                    if coords:
                        points_data[point_id] = coords
    except (KeyError, TypeError, IndexError):
        pass

    # Extract lines
    try:
        if 'geos' in data and 'lines' in data['geos']:
            for line in data['geos']['lines']:
                if line and 'loc' in line and line['loc'] and len(line['loc']) >= 2:
                    lines_data.append({
                        'start': line['loc'][0],
                        'end': line['loc'][1],
                        'id': line.get('id', '')
                    })
    except (KeyError, TypeError, IndexError):
        pass

    # Extract circles
    try:
        if 'geos' in data and 'circles' in data['geos']:
            for circle in data['geos']['circles']:
                if (circle and 'loc' in circle and circle['loc'] and
                        len(circle['loc']) >= 2):
                    circles_data.append({
                        'center': circle['loc'][0],
                        'radius': circle['loc'][1],
                        'id': circle.get('id', '')
                    })
    except (KeyError, TypeError, IndexError):
        pass

    # Extract symbols and labels
    try:
        if 'symbols' in data and data['symbols']:
            for symbol in data['symbols']:
                if symbol:
                    sym_class = symbol.get('sym_class', '')
                    symbol_id = symbol.get('id')

                    associated_geo = None
                    if ('relations' in data and
                            'sym2geo' in data['relations']):
                        for sym_geo in data['relations']['sym2geo']:
                            if (len(sym_geo) >= 2 and sym_geo[0] == symbol_id):
                                associated_geo = sym_geo[1]
                                break

                    if sym_class == 'text':
                        text_content = symbol.get('text_content', '')
                        text_class = symbol.get('text_class', 'others')

                        position = None
                        if 'bbox' in symbol and symbol['bbox'] and len(symbol['bbox']) >= 4:
                            bbox = symbol['bbox']
                            center_x = bbox[0] + bbox[2] / 2
                            center_y = bbox[1] + bbox[3] / 2
                            position = [center_x, center_y]
                        elif 'loc' in symbol and symbol['loc']:
                            position = symbol['loc']
                        else:
                            position = [100, 100]

                        all_labels.append({
                            'text': text_content,
                            'position': position,
                            'associated_geo': associated_geo,
                            'symbol_id': symbol_id,
                            'text_class': text_class
                        })

                    elif sym_class == 'perpendicular':
                        markers['perpendicular'].append({
                            'associated_geo': associated_geo,
                            'symbol_id': symbol_id
                        })

                    elif sym_class in ['angle', 'double angle', 'triple angle', 'quad angle', 'penta angle']:
                        markers['angle'].append({
                            'associated_geo': associated_geo,
                            'symbol_id': symbol_id,
                            'original_class': sym_class
                        })
    except (KeyError, TypeError, IndexError):
        pass

    # Build the geometry
    all_elements = []
    points_dict = {}
    lines_dict = {}

    # Create points
    for point_id, coords in points_data.items():
        try:
            manim_coords = convert_coordinates(coords)
            dot = Dot(manim_coords, color=RED, radius=0.05)
            points_dict[point_id] = dot
            all_elements.append(dot)
        except:
            continue

    # Create labels with color coding
    text_class_colors = {
        'point': BLACK,
        'degree': BLUE,
        'line': PURPLE,
        'angle': GREEN,
        'length': ORANGE,
        'area': YELLOW,
        'len': ORANGE,
        'others': GRAY
    }

    for label_data in all_labels:
        try:
            text_content = label_data['text']
            if not text_content.strip():
                continue

            text_class = label_data.get('text_class', 'others')
            display_text = text_content

            # Special handling for degree symbols
            if text_class == 'degree':
                if "°" not in text_content and any(c.isdigit() for c in text_content):
                    display_text = text_content + "°"
                text_color = GREEN if any(c.isalpha() for c in text_content) else BLUE
            else:
                text_color = text_class_colors.get(text_class, GRAY)

            manim_coords = convert_coordinates(label_data['position'])
            label_obj = Text(display_text, font_size=20, color=text_color)
            label_obj.move_to(manim_coords)
            all_elements.append(label_obj)
        except:
            continue

    # Create lines
    for line in lines_data:
        try:
            start_coords = convert_coordinates(line['start'])
            end_coords = convert_coordinates(line['end'])
            line_obj = Line(start_coords, end_coords, color=BLACK, stroke_width=2)
            lines_dict[line['id']] = line_obj
            all_elements.append(line_obj)
        except:
            pass

    # Create circles
    for circle in circles_data:
        try:
            center_coords = convert_coordinates(circle['center'])
            radius = circle['radius'] * scale_factor
            circle_obj = Circle(radius=radius, color=YELLOW, stroke_width=2)
            circle_obj.move_to(center_coords)
            all_elements.append(circle_obj)
        except:
            pass

    # Create perpendicular markers
    for marker in markers['perpendicular']:
        try:
            geo = marker['associated_geo']

            if not geo or len(geo) < 3:
                continue

            point_id, line1_id, line2_id = geo[0], geo[1], geo[2]

            if line1_id not in lines_dict or line2_id not in lines_dict:
                continue

            line1 = lines_dict[line1_id]
            line2 = lines_dict[line2_id]

            intersection = line_intersection_point(line1, line2)

            if intersection is None and point_id in points_dict:
                intersection = points_dict[point_id].get_center()
            elif intersection is None:
                continue

            square = create_right_angle_marker(intersection, line1, line2, size=0.12)
            all_elements.append(square)

        except Exception:
            continue

    # Create angle markers
    for marker in markers['angle']:
        try:
            geo = marker['associated_geo']
            original_class = marker.get('original_class', 'angle')

            if not geo or len(geo) < 3:
                continue

            point_id, line1_id, line2_id = geo[0], geo[1], geo[2]

            if line1_id not in lines_dict or line2_id not in lines_dict:
                continue

            line1 = lines_dict[line1_id]
            line2 = lines_dict[line2_id]

            intersection = line_intersection_point(line1, line2)

            if intersection is None and point_id in points_dict:
                intersection = points_dict[point_id].get_center()
            elif intersection is None:
                continue

            num_arcs = 1
            if "double" in original_class:
                num_arcs = 2
            elif "triple" in original_class:
                num_arcs = 3
            elif "quad" in original_class:
                num_arcs = 4
            elif "penta" in original_class:
                num_arcs = 5

            for j in range(num_arcs):
                radius = 0.25 - (j * 0.04)
                if radius > 0.05:
                    arc = create_angle_arc_marker(intersection, line1, line2, radius=radius)
                    all_elements.append(arc)

        except Exception:
            continue

    # Return the complete geometry as a VGroup
    if all_elements:
        geometry_group = VGroup(*all_elements)
        geometry_group.move_to(ORIGIN)
        return geometry_group
    else:
        return VGroup()