import { RichTreeView } from "@mui/x-tree-view/RichTreeView";

const sampleTree = [
  {
    id: "src",
    label: "src",
    children: [
      { id: "src/components", label: "components", children: [{ id: "src/components/Button.tsx", label: "Button.tsx" }] },
      { id: "src/main.tsx", label: "main.tsx" },
      { id: "src/App.tsx", label: "App.tsx" },
    ],
  },
  {
    id: "package.json",
    label: "package.json",
  },
  {
    id: "tsconfig.json",
    label: "tsconfig.json",
  },
  { id: "README.md", label: "README.md" },
];

const FileTree = ({ onSelectFile }) => {
  return (
    <RichTreeView
      items={sampleTree}
      defaultExpandedItems={["src", "src/components"]}
      onItemClick={(_event, itemId) => {
        // Only fire for leaf-like entries (no children). For this scaffold we
        // pass any clicked id through so the editor can react.
        onSelectFile?.(itemId);
      }}
      sx={{
        flex: 1,
        overflowY: "auto",
        py: 1,
        "& .MuiTreeItemContent": { py: 0.4, borderRadius: 1 },
        "& .MuiTreeItemLabel": { fontSize: 13, color: "#e6edf3" },
      }}
    />
  );
};

export default FileTree;