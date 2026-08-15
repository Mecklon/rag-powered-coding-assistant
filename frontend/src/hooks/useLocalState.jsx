import { useEffect, useState } from "react";

const useLocalState = (key, initialValue) => {
  const [state, setState] = useState(() => {
    try {
      const localValue = JSON.parse(localStorage.getItem(key));
      if (localValue != null && localValue !== "undefined") {
        return localValue;
      } else {
        return initialValue;
      }
    } catch (error) {
      console.log("something went wrong " + error);
    }
  });


  useEffect(()=>{
    try{
        localStorage.setItem(key, JSON.stringify(state));
    }catch(error){
        console.log("error while storing to local storage "+ error)
    }
  },[key, state])

  return [state, setState]
};

export default useLocalState;
