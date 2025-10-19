import {makeProject} from '@imaginix-inc/motion-canvas-core';

import #(scene_name) from './scenes/#(scene_name)?scene';
export default makeProject({
  experimentalFeatures: true,
  scenes: [#(scene_name)],
});
