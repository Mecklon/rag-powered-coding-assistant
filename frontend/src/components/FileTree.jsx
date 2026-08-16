import { RichTreeView } from "@mui/x-tree-view/RichTreeView";

/**
 * Builds a nested {id,label,children} structure from a flat list of file paths.
 */
const buildTree = (paths) => {
  const root = { children: [] };

  for (const path of paths) {
    const parts = path.split("/");
    let node = root;
    let currentPath = "";
    for (let i = 0; i < parts.length; i++) {
      currentPath = currentPath ? `${currentPath}/${parts[i]}` : parts[i];
      const isFile = i === parts.length - 1;
      let child = node.children.find((c) => c.id === currentPath);
      if (!child) {
        child = { id: currentPath, label: parts[i], children: [] };
        node.children.push(child);
      }
      node = child;
    }
    if (node.children.length === 0) {
      delete node.children;
    }
  }
  return root.children;
};

const FileTree = ({ paths, onSelectFile }) => {
  const items = buildTree(paths || []);

  return (
    <RichTreeView
      items={items}
      onItemClick={(_event, itemId) => {
        // Only file paths (which are exactly the leaf blobs from the backend)
        // are selectable. Folders are not in `paths`, so clicking them is a no-op.
        if ((paths || []).includes(itemId)) {
          onSelectFile?.(itemId);
        }
      }}
      sx={{
        flex: 1,
        minHeight: 0,
        overflowX: "auto",
        overflowY: "auto",
        py: 1,
        "& .MuiTreeItemContent": { py: 0.4, borderRadius: 1 },
        "& .MuiTreeItemLabel": {
          fontSize: 13,
          color: "#e6edf3",
          whiteSpace: "nowrap",
          overflow: "hidden",
          textOverflow: "ellipsis",
        },
      }}
    />
  );
};

export default FileTree;