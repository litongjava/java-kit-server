import {makeProject} from '@imaginix-inc/motion-canvas-core';

#for(scene_name : sceneNames)
import #(scene_name) from './scenes/#(scene_name)?scene';
#end

export default makeProject({
  experimentalFeatures: true,
  scenes: [#for(scene_name : sceneNames) #(scene_name)#if(!for.last), #end#end],
});