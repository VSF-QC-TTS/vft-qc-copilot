import { useOutletContext } from 'react-router';
import { WorkspaceContextType } from '@/app/layouts/workspace-context-layout';
import type { ProjectResponse } from '@/features/projects/types/project';

interface ProjectContextType extends WorkspaceContextType {
  project: ProjectResponse;
}

export function ProjectOverviewPage() {
  const { workspace, project } = useOutletContext<ProjectContextType>();

  return (
    <div className="mx-auto w-full max-w-5xl py-24 px-4 sm:px-6 lg:px-8">
      <div className="mb-12">
        <div className="space-y-1">
          <div className="mb-2 flex items-center gap-2">
            <span className="flex items-center rounded-full bg-[#E1F3FE] px-2.5 py-0.5 text-xs font-medium uppercase tracking-wide text-[#1F6C9F]">
              Project
            </span>
          </div>
          <h1 className="text-4xl font-semibold tracking-tight text-[#111111]" style={{ fontFamily: "'Lyon Text', 'Newsreader', 'Playfair Display', serif" }}>
            {project.name}
          </h1>
          <p className="text-base text-[#787774]">
            ID: <kbd className="rounded border border-[#EAEAEA] bg-[#F7F6F3] px-1 font-mono text-xs">{project.id}</kbd>
          </p>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-6 md:grid-cols-3">
        <div className="col-span-2 space-y-6">
          <div className="rounded-xl border border-[#EAEAEA] bg-white p-6">
            <h2 className="text-lg font-medium text-[#111111]">Mô tả</h2>
            <div className="mt-4 text-[#787774]">
              {project.description ? (
                <p>{project.description}</p>
              ) : (
                <p className="italic">Không có mô tả cho project này.</p>
              )}
            </div>
          </div>
        </div>

        <div className="space-y-6">
          <div className="rounded-xl border border-[#EAEAEA] bg-[#FBFBFA] p-6">
            <h3 className="text-sm font-medium text-[#111111]">Thông tin</h3>
            <dl className="mt-4 space-y-4 text-sm">
              <div>
                <dt className="text-[#787774]">Workspace</dt>
                <dd className="mt-1 font-medium text-[#111111]">{workspace.name}</dd>
              </div>
              <div>
                <dt className="text-[#787774]">Ngày tạo</dt>
                <dd className="mt-1 font-medium text-[#111111]">
                  {new Date(project.createdAt).toLocaleDateString()}
                </dd>
              </div>
              {project.updatedAt && (
                <div>
                  <dt className="text-[#787774]">Cập nhật lần cuối</dt>
                  <dd className="mt-1 font-medium text-[#111111]">
                    {new Date(project.updatedAt).toLocaleDateString()}
                  </dd>
                </div>
              )}
            </dl>
          </div>
        </div>
      </div>
    </div>
  );
}
